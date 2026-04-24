# company-onboarding — aprofundamento

> Este documento complementa o `README.md`. Enquanto o README explica **o que** cada
> pattern faz e **por quê** foi escolhido, aqui entramos em **como funciona por baixo**,
> **quais são os trade-offs** e **onde a arquitetura atual pode te morder**.

---

## 1. Fluxo ponta-a-ponta detalhado

Quando um POST chega em `/api/v1/companies/onboard`:

### a) Spring decodifica o corpo e valida

Jakarta Bean Validation (`@Valid` → `@NotBlank`, `@Email`…). Se falhar, 400 antes
mesmo do controller executar. Isso é **fail-fast de input**, responsabilidade do
adapter HTTP; o Ring 2 nunca vê dado inválido.

### b) Controller mapeia `OnboardCompanyRequest` → `OnboardCompanyInput`

Via `toInput(idempotencyKey)`. Parece redundante, mas:

- impede que anotações de validação (`@NotBlank`) vazem para o Ring 2 — elas são
  um **detalhe de transporte**;
- desacopla a forma HTTP da forma do caso de uso. Amanhã um `MessageListenerAdapter`
  lê a mesma coisa de uma fila — ele constrói o `OnboardCompanyInput` sem passar
  por record com anotações.

### c) Controller cria um presenter por request

E injeta na `execute(...)`. O presenter é **stateful** (guarda a `ResponseEntity`).
Isso é importante: por ser criado a cada request, não precisa ser thread-safe.
Um presenter singleton precisaria `ThreadLocal` ou devolver o valor — cairia no
mesmo ponto.

### d) Interactor checa idempotência

Se achar job, presenter responde 200 e retorna. **Nota**: o 200 é consciente —
202 foi para "aceitei e vou processar"; em replay *nada novo* vai acontecer,
então 200 com corpo idempotente é o certo.

### e) Interactor cria `OnboardingJob.pending(...)`

Estado PENDING, 0 steps completos. `OnboardingJob` é **mutável de propósito**
(diferente de `Company`, record imutável). Razão: um job tem ciclo de vida, e
as regras de transição (`PENDING → IN_PROGRESS → COMPLETED/FAILED`) valem a
pena viver no objeto via métodos como `start()` e `fail()`, que lançam
`IllegalStateException` se o estado atual for incompatível.

### f) `jobGateway.save(job)` — aqui pode explodir

O `INSERT` bate na unique partial index. Se já existir job ativo para essa
empresa, `DataIntegrityViolationException` sobe até `GlobalExceptionHandler`.
O erro vira 409 **fora do interactor** — ele fica ingênuo.

### g) `orchestratorExecutor.execute(...)`

Fire-and-forget em virtual thread. O interactor sai imediatamente, o presenter
responde 202. Essa linha é o ponto que separa síncrono de assíncrono.

### h) Na virtual thread

1. `runSafely` envelopa tudo em try/catch — se qualquer coisa fora dos steps
   explodir, marca FAILED e publica progresso.
2. `run`: busca o job do DB, chama `job.start()` (PENDING → IN_PROGRESS), salva,
   publica.
3. **Loop pelos steps em ordem**. Cada step chama `step.execute(ctx)` — passa por
   proxy CGLIB porque `@Transactional(REQUIRES_NEW)` está na template `execute()`.
   Proxy abre tx nova, commita ao sair.
4. Entre steps: re-busca do DB, atualiza contadores, salva, publica. **Re-busca
   sempre**: evita segurar uma referência stale enquanto o banco pode ter mudado.
5. Ao fim: `job.complete()` → 100%, publica último evento.

### i) Em paralelo, o cliente abriu SSE

Cada `progressPublisher.publish(job)` → `SseProgressPublisher` converte para JSON
→ `SseEmitterRegistry.broadcast(...)` envia para emitters registrados.

A ordem importa: **o snapshot inicial é enviado DEPOIS de registrar o emitter**.
Se fosse antes, um `publish()` chegando entre "enviou snapshot" e "registrou"
seria perdido. Com a ordem atual, o cliente pode receber snapshot + próximo
evento (parcialmente duplicado), mas nunca *perde* um evento.

---

## 2. SseEmitter em profundidade

### 2.1 O que é, por baixo

Server-Sent Events é texto simples em cima de uma resposta HTTP **que nunca
fecha**. O servidor escreve:

```
event: progress
id: 1712336400000
data: {"jobId":"...","percent":25}

event: progress
data: {...}
```

Headers: `Content-Type: text/event-stream`, `Cache-Control: no-cache`,
`Connection: keep-alive`.

No Spring MVC, `SseEmitter` é uma abstração assíncrona: o controller retorna
`SseEmitter`, o Spring **não** finaliza a resposta, e a thread do Tomcat é
liberada (`AsyncContext.startAsync()` é chamado internamente). Cada
`emitter.send(...)` escreve um chunk.

### 2.2 Por que SSE e não WebSocket?

- **Progresso unidirecional** (server → client) — SSE é mais simples: nada de
  handshake, nada de subprotocolo, só HTTP.
- Funciona em HTTP/1.1 e HTTP/2, passa por proxies que entendem streaming.
- O browser reconecta sozinho se a conexão cair (sem lib).
- WebSocket ganha quando precisa **bidirecional** de baixa latência (chat, jogos).

### 2.3 Ciclo de vida

```java
SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);    // 10 min
emitterRegistry.register(id, emitter);                   // lista + callbacks
getJobProgress.byId(id).ifPresent(o -> emitter.send(...));
return emitter;
```

No `register()`, ligamos `onCompletion`, `onTimeout`, `onError`. Essas callbacks
rodam quando:

- cliente fecha a aba (TCP FIN) → `onCompletion` / `onError`;
- timeout do emitter estoura → `onTimeout`;
- `emitter.complete()` explícito → `onCompletion`.

Todas removem o emitter do mapa. Sem isso, o `Map<UUID, List<SseEmitter>>` vaza
memória para sempre.

### 2.4 Concorrência na registry

O orquestrador publica em **virtual thread**. O Tomcat ainda registra/cancela
emitters em threads de request. Duas threads mexem na mesma
`List<SseEmitter>`. Por isso:

- `ConcurrentHashMap` no outer — múltiplos jobs broadcast/registrados ao mesmo tempo.
- `CopyOnWriteArrayList` no inner — iterar durante broadcast enquanto outra
  thread remove.

`CopyOnWriteArrayList` tem custo: cada `add/remove` copia o array inteiro. Aqui
vale porque a razão *leituras:escritas* é favorável (broadcast a cada step,
add/remove uma vez por emitter) e o array é pequeno (tipicamente 1–3 abas abertas).

### 2.5 `send()` dentro do broadcast

Se um cliente morreu e tentamos enviar, `IOException` sobe. A rotina captura e
**remove aquele emitter**. Não propaga — um cliente morto não pode estragar o
broadcast para os outros.

### 2.6 Problemas operacionais

- **Proxies** que fazem buffer (nginx padrão) seguram eventos até ter "bastante"
  bytes. Setar `proxy_buffering off` e `X-Accel-Buffering: no`.
- **Load balancers sem sticky session**: se o cliente reconecta em outro nó, o
  outro nó não tem emitter nem sabe do progresso. Em cluster real, Redis pub/sub
  ou broker distribui o `publish()` entre nós.
- **Timeout hardcoded 10 min**: se job demora mais, emitter expira. Browser
  reconecta — e como job ainda está IN_PROGRESS, snapshot inicial serve de
  catch-up. Eventos entre timeout e reconexão podem ser perdidos (sem
  `Last-Event-ID` handling).
- **Conexões simultâneas**: cada cliente = socket + thread. Com virtual threads
  + servlet async, não é problema prático.

### 2.7 Detalhe fino: `id:` no evento

O código usa `String.valueOf(System.currentTimeMillis())`. Não é ideal:

- não é monotônico entre nós;
- se o browser reconectar, ele manda `Last-Event-ID` com o último id visto — o
  servidor **poderia** usar isso para re-enviar só o que faltou. Hoje ignoramos
  esse header e reenviamos o snapshot inteiro. Para este projeto, ok — snapshot
  é pequeno e idempotente.

---

## 3. Idempotency Key em profundidade

### 3.1 Conceito

Ideia vem de APIs comerciais (Stripe popularizou): o cliente gera um UUID, manda
no header `Idempotency-Key`, e o servidor **garante** que **uma operação com
aquele key acontece no máximo uma vez**, mesmo se o cliente retentar.

Por que importa:

- Redes falham. Cliente manda POST, timeout, **não sabe** se chegou.
- Sem idempotência, retentar pode criar duas empresas.
- Com idempotência, o segundo POST é uma consulta disfarçada.

### 3.2 Como está implementado

```sql
CREATE TABLE onboarding_job (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    ...
);
```

```java
var existing = jobGateway.findByIdempotencyKey(input.idempotencyKey());
if (existing.isPresent()) {
    presenter.presentAlreadyExists(new OnboardCompanyOutput(
        job.id(), job.status(), job.totalSteps()));
    return;
}
```

Dois mecanismos combinados:

1. **Consulta prévia** no caso feliz: retorna rápido sem tentar inserir nada.
2. **Unique constraint** no DB: se dois requests com mesmo key chegarem
   *simultaneamente* e ambos virem `empty` na consulta, o INSERT de um dos dois
   explode com DataIntegrityViolation.

### 3.3 Bug latente (consertar em próximo round)

O `GlobalExceptionHandler` só traduz `uq_onboarding_job_active_company` para 409.
Se o conflito for no UNIQUE de `idempotency_key`, cai no else → 400. **Deveria**
tratar o conflito re-consultando e respondendo 200 (como se fosse replay).

Exercício: adicionar um catch em `OnboardCompanyInteractor.execute()` que, ao
pegar `DataIntegrityViolationException` com mensagem mencionando
`idempotency_key`, re-chame `findByIdempotencyKey` e responda como replay.

### 3.4 Stripe faz uma coisa a mais

Stripe guarda um **hash do corpo** junto com o key. Se o cliente manda o MESMO
key com payload DIFERENTE, Stripe responde erro (`Keys can only be used with
the same parameters`). Aqui ignoramos silenciosamente — retornamos o job antigo
e descartamos o payload novo. Em produção, pegadinha séria.

### 3.5 Ciclo de vida da chave

A chave fica na tabela pra sempre. Problemas:

- Tabela cresce sem limite.
- Replay funcional é útil por minutos/horas, não por anos.

Soluções típicas:

- **TTL** — expurgar chaves antigas com scheduler
  (`DELETE FROM onboarding_job WHERE status IN ('COMPLETED','FAILED')
  AND updated_at < now() - interval '30 days'`).
- Tabela separada `idempotency_record` com TTL + `onboarding_job` histórico.

---

## 4. Modelagem da tabela `onboarding_job`

```sql
CREATE TABLE onboarding_job (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(200) NOT NULL UNIQUE,
    company_name     VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    step_index       INTEGER      NOT NULL DEFAULT 0,
    total_steps      INTEGER      NOT NULL,
    current_step     VARCHAR(100),
    percent          INTEGER      NOT NULL DEFAULT 0,
    failed_step      VARCHAR(100),
    error_message    TEXT,
    company_id       UUID REFERENCES company(id),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

### 4.1 `id UUID`, não `bigserial`

O id é gerado pelo *domínio* (`OnboardingJob.pending()` faz `UUID.randomUUID()`)
antes de tocar no banco. Isso deixa Ring 1/2 independente da geração de chave.
UUIDs podem ser trocados por distribuídos (UUIDv7 tem ordenação temporal, bom
para índice).

**Trade-off**: UUID ocupa 16 bytes, serial 8 — índices ficam maiores. Para
projeto didático, não é gargalo.

### 4.2 `idempotency_key VARCHAR(200) NOT NULL UNIQUE`

VARCHAR porque a chave vem do cliente (pode ser UUID ou string qualquer). 200
é folga para quem quiser usar, por exemplo, `<requestId>:<version>`.

### 4.3 `status VARCHAR(20)`, não enum nativo

Postgres tem `CREATE TYPE ... AS ENUM`, mas migrá-lo é chato (precisa `ALTER
TYPE ADD VALUE`). VARCHAR + string no código é mais maleável. O JPA usa
`@Enumerated(EnumType.STRING)` — armazena o nome do valor.

### 4.4 `step_index + total_steps + percent`

Redundância controlada. `percent` é derivável, mas armazenar explicitamente
simplifica a leitura (front não recalcula) e permite UX como "70%" mesmo sem
cruzar fronteiras de step.

### 4.5 `failed_step + error_message`

Nullable. Só preenchidos em FAILED. Alternativa: tabela `job_event` com
histórico — mais power, mais complexo. Aqui estado final basta.

### 4.6 `version BIGINT` + `@Version` JPA

**Optimistic locking**. Se dois caminhos tentarem salvar o mesmo job
simultaneamente, o UPDATE `WHERE id = ? AND version = ?` falha para o segundo.
No fluxo atual, quem escreve é só o orquestrador — em teoria nunca colide.
Mas deixar lá é barato e protege contra surpresas.

### 4.7 `created_at` / `updated_at` TIMESTAMPTZ

**Sempre com timezone**. Nunca use `TIMESTAMP WITHOUT TIME ZONE` em Postgres
para eventos — vira bug de DST.

No JPA:

```java
@Column(insertable = false, updatable = false)
private OffsetDateTime createdAt;  // DEFAULT now() do SQL

@Column private OffsetDateTime updatedAt;

@PrePersist void onInsert() { this.updatedAt = OffsetDateTime.now(); }
@PreUpdate  void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
```

`created_at` é setado pelo Postgres. Por isso `insertable = false` — JPA não
tenta mandar NULL.

### 4.8 O índice único parcial

```sql
CREATE UNIQUE INDEX uq_onboarding_job_active_company
    ON onboarding_job (company_name)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
```

Feature Postgres (não existe em MySQL padrão): índice **condicional**. Postgres
aplica unicidade **só** em linhas que satisfazem o WHERE. Efeito: no máximo
**uma** linha com status ativo por `company_name`. Jobs completos/falhos podem
coexistir à vontade.

Esta é a **garantia real** de "um onboarding ativo por empresa". O advisory
lock não é essencial — é só atalho para falhar rápido antes do INSERT.

### 4.9 Flyway

`V1__init.sql` em `src/main/resources/db/migration/`. Convenção:

- prefixo `V`, versão, `__`, descrição, `.sql`;
- executa uma vez, registra em `flyway_schema_history`;
- `ddl-auto: validate` no Hibernate valida schema ↔ JPA entities, mas **não cria
  nada**. Quem cria é Flyway. Disciplina: schema é artefato versionado em SQL,
  não derivado de Java.

---

## 5. Possíveis problemas da arquitetura adotada

### 5.1 No orquestrador

- **Jobs órfãos**: se a JVM cai no meio de um step, o job fica em `IN_PROGRESS`
  para sempre. Consequência dupla: (a) o índice parcial unique bloqueia retry
  para aquela empresa; (b) cliente espera SSE que nunca vem.
  **Correção**: scheduler (`@Scheduled`) marca como FAILED jobs `IN_PROGRESS`
  com `updated_at < now() - interval '15 min'`.

- **Idempotency replay retorna job travado**: se o job está "órfão", replay
  devolve `IN_PROGRESS` e cliente acha que ainda está em andamento. Precisa
  do reaper acima.

- **Falta de retry por step**: falha transitória (deadlock, timeout DB) falha
  o job inteiro. Em produção, retry com backoff em falhas retryable
  (`CannotAcquireLockException`, `PSQLException` com códigos específicos) seria
  razoável — mas aumenta complexidade de idempotência entre steps.

### 5.2 No modelo de dados

- **Child entities sem `@Version` e com `@Id` pré-atribuído**: Spring Data JPA
  faz `SELECT ... WHERE id = ?` antes de cada `save()` para decidir insert vs
  update. Com 10 mil employees: 10 mil SELECTs + 10 mil INSERTs.
  Soluções: implementar `Persistable<UUID>` e forçar `isNew() = true`; ou usar
  `entityManager.persist()` explícito. Para este projeto, a lentidão é
  bem-vinda (demo); em produção, inaceitável.

- **Sem batch inserts**: mesmo com `saveAll`, Hibernate não faz
  `INSERT INTO ... VALUES (...), (...), ...` por padrão. Precisa
  `spring.jpa.properties.hibernate.jdbc.batch_size=50` + `order_inserts=true`.

### 5.3 Na concorrência

- **Colisão de `hashtext()`**: `pg_try_advisory_xact_lock(hashtext(key))` reduz
  a string a um int32. Colisões existem. Duas empresas com hashes iguais se
  bloqueariam mutuamente por microssegundos. Probabilidade ~1/2³² — para esse
  projeto, nada. Para um sistema com milhões de nomes, usar
  `pg_try_advisory_xact_lock(bigint)` com hash mais longo ou dois ints (a
  função tem overload de dois parâmetros).

- **Advisory lock + índice parcial = duas barreiras**: **defensive programming**
  deliberado. Em teoria o índice basta. O lock melhora a mensagem de erro
  (`IllegalStateException` clara) em vez de `DataIntegrityViolation` genérico.
  Poderia ser removido sem quebrar correção. Mantido para fins didáticos.

### 5.4 No SSE

- **Single-node only**: `SseEmitterRegistry` vive em memória. Em cluster,
  cliente conectado no nó A não recebe progresso do job rodando no nó B. Em
  produção: publicar o evento em Redis pub/sub (ou Kafka); cada nó assina;
  `SseProgressPublisher` vira publisher de mensagem + listener por nó repassa
  para emitters locais.

- **Ordem de eventos pode inverter sob carga**: broadcast é síncrono dentro do
  publish, mas se o orquestrador chamar `publish(1)` numa thread e `publish(2)`
  em outra (não acontece no design atual, single-thread por job), não há
  ordenação garantida. Se viesse `Last-Event-ID` handling, o id monotônico teria
  que ser gerado com cuidado (ex. contador por job).

- **Sem `Last-Event-ID` handling**: reconexão faz o cliente começar do snapshot
  atual. Se o cliente piscou durante um step, perdeu o evento desse step mas o
  percent no próximo o corrige visualmente.

### 5.5 Na idempotência

- **Mesmo key com payloads diferentes é aceito silenciosamente** (§3.4).
- **Race do segundo POST simultâneo vira 500/400, não 200** — o
  `GlobalExceptionHandler` não trata o conflito do unique `idempotency_key`
  (§3.3).

### 5.6 Em Clean Architecture aqui

- **`ProgressPublisher` recebe `OnboardingJob` inteiro**, não eventos de
  domínio. Isso acopla o adapter ao modelo. Em Clean "purista", emitiríamos
  `JobProgressed(jobId, step, percent)` — *event object* separado. Trade-off:
  mais tipos, mais fidelidade à teoria. Aqui: simplicidade.

- **`OnboardingJobGatewayJpa.save()` é `@Transactional`** — chamado de dentro
  do loop do interactor, **fora** do REQUIRES_NEW do step. Abre tx nova só para
  esse save. Cada save = conexão do pool + ida ao DB. Alternativa: interactor
  declararia tx envelope — mas aí progresso intermediário só viraria visível
  no final. Escolhido: visibilidade > menos txs.

- **`CreationStepContext` é mutável e usa `Map<String, Object>`**: funcionou
  aqui porque só passa `companyId`. Se crescer (10 chaves, 20 tipos), vira
  frágil. Refactor possível: `sealed interface StepContext` com variações tipadas.

### 5.7 Em virtual threads (Java 25)

- **Pinning**: Java 21 ainda pinnava em `synchronized`. Java 24/25 resolveu —
  `synchronized` não pinna mais. Hibernate e HikariCP são safe.
- **Mas**: virtual threads **não** herdam o `TransactionSynchronizationManager`
  automaticamente se você escapar do request scope. No caso, o executor submete
  direto para uma virtual thread nova e a tx é aberta de dentro dela (pelo
  proxy `@Transactional`), então funciona. Passar uma tx do controller para
  o executor explodiria — alguns projetos fazem errado.

### 5.8 Escalabilidade

- **Tabela `onboarding_job` cresce indefinidamente** (sem TTL). Índice unique
  parcial funciona até escala X, depois vira sorte. Planejamento: particionar
  por `created_at` trimestral, ou mover COMPLETED para tabela de histórico.

- **Pool HikariCP (max 20)**: com 100 jobs concorrentes, cada um com steps em
  REQUIRES_NEW pegando conexão, a fila vira gargalo rápido. Projeto didático,
  ok. Produção: ajustar pool conforme QPS alvo, ou mover orquestração para
  fila (SQS/RabbitMQ) em vez de virtual thread direta.

---

## Referências para aprofundar

### Clean Architecture

- **"Clean Architecture: A Craftsman's Guide to Software Structure and Design"**
  — Robert C. Martin (livro). Capítulos 15–22 cobrem exatamente as camadas e
  regra de dependência usadas aqui.
- **"The Clean Architecture"** — post original:
  https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- **"Hexagonal Architecture"** — Alistair Cockburn (origem do conceito de ports
  & adapters, que Clean Arch sintetiza):
  https://alistair.cockburn.us/hexagonal-architecture/
- **"Get Your Hands Dirty on Clean Architecture"** — Tom Hombergs (livro; exemplos
  em Spring Boot, bem próximos do estilo deste projeto).

### Patterns clássicos usados

- **"Design Patterns: Elements of Reusable Object-Oriented Software"** — Gang
  of Four. Strategy (§Strategy), Template Method (§Template Method), Observer
  (§Observer).
- **"Patterns of Enterprise Application Architecture"** — Martin Fowler.
  Gateway (§Gateway), Repository (§Repository), Unit of Work.
- **Refactoring.guru** — explicações visuais e exemplos curtos:
  https://refactoring.guru/design-patterns

### Server-Sent Events

- **MDN — Using Server-Sent Events**:
  https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
- **HTML Living Standard — Server-Sent Events** (a especificação):
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Spring Framework — Async Servlet & SseEmitter**:
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- **"Server-Sent Events: the alternative to WebSockets you should be using"**
  — Germano Gabbianelli (discussão prática de quando SSE é a escolha certa).

### Idempotency Key

- **"Designing robust and predictable APIs with idempotency"** — Stripe blog:
  https://stripe.com/blog/idempotency
- **IETF draft — The Idempotency-Key HTTP Header Field**:
  https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- **"Implementing Stripe-like Idempotency Keys in Postgres"** — Brandur Leach:
  https://brandur.org/idempotency-keys

### Postgres: advisory locks & partial indexes

- **Docs oficiais — Advisory Locks**:
  https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS
- **Docs oficiais — Partial Indexes**:
  https://www.postgresql.org/docs/current/indexes-partial.html
- **"Advisory locks and how to use them"** — Thoughtbot:
  https://thoughtbot.com/blog/advisory-locks-in-postgres

### Transações e Spring

- **Spring Framework — Transaction Management**:
  https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- **"Transaction Propagation and Isolation in Spring @Transactional"** — Baeldung:
  https://www.baeldung.com/spring-transactional-propagation-isolation
- **"Optimistic Locking in JPA"** — Vlad Mihalcea:
  https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/

### Virtual Threads (Java 21+)

- **JEP 444 — Virtual Threads** (feature final em 21):
  https://openjdk.org/jeps/444
- **JEP 491 — Synchronize Virtual Threads without Pinning** (resolveu pinning
  em 24): https://openjdk.org/jeps/491
- **"Spring Boot 3.2 + Virtual Threads"** — docs Spring Boot:
  https://spring.io/blog/2023/11/23/spring-boot-3-2-0-available-now#virtual-threads-support
- **"Coming to Java 25: Flexible Constructor Bodies, Stable Values, Virtual
  Threads tuning"** — Inside Java:
  https://inside.java/

### Flyway & schema migrations

- **Docs oficiais — Getting Started**:
  https://documentation.red-gate.com/flyway/getting-started-with-flyway
- **"Evolutionary Database Design"** — Martin Fowler (teoria por trás):
  https://martinfowler.com/articles/evodb.html

### MapStruct

- **Docs oficiais**: https://mapstruct.org/documentation/reference-guide/
- **"MapStruct — the Performance Champ of Java Bean Mapping"** — Baeldung:
  https://www.baeldung.com/mapstruct

### JPA & Hibernate avançado

- **"High-Performance Java Persistence"** — Vlad Mihalcea (livro). Capítulos
  sobre batch, select-before-save, flush strategies são diretamente relevantes
  aos problemas levantados em §5.2.
- **vladmihalcea.com** — blog do mesmo autor, material denso e preciso.

### REST e assincronia

- **RFC 7231 — HTTP/1.1 Semantics (status 202)**:
  https://datatracker.ietf.org/doc/html/rfc7231#section-6.3.3
- **"Asynchronous Request-Reply Pattern"** — Microsoft Learn (o pattern que
  o `202 + /status` implementa):
  https://learn.microsoft.com/en-us/azure/architecture/patterns/async-request-reply

### Patterns de resiliência (tópicos citados nos "próximos passos")

- **"Saga Pattern"** — microservices.io:
  https://microservices.io/patterns/data/saga.html
- **"Transactional Outbox"** — microservices.io:
  https://microservices.io/patterns/data/transactional-outbox.html
- **"Designing Data-Intensive Applications"** — Martin Kleppmann (capítulos 7,
  8 e 11 cobrem idempotência distribuída, consistência e entrega de mensagens).
