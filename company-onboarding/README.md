# company-onboarding

> Projeto de estudo de **Clean Architecture** aplicada a um **job assíncrono** com
> **progresso em tempo real via SSE**. Aqui a gente exercita vários patterns
> trabalhando juntos em um cenário realista.

---

## O que este projeto ensina

Ao ler o código e acompanhar o fluxo você vai praticar, em ordem de importância:

1. **Clean Architecture** — como separar regras de negócio do framework.
2. **Input/Output boundaries** — por que o controller não chama o "service" direto.
3. **Presenter pattern** — quem decide o HTTP status?
4. **Gateway pattern** — portas de saída no domínio, adaptadores JPA fora.
5. **Strategy + Template Method** — as 4 etapas do onboarding como beans independentes.
6. **Idempotency Key** — como aceitar o mesmo POST duas vezes sem criar dois jobs.
7. **Virtual Threads para orquestração assíncrona** — o "fire and forget" correto.
8. **Observer / Publisher-Subscriber** — orquestrador publica, SSE reage.
9. **Concorrência com Postgres** — advisory lock + índice único parcial.
10. **Transações por etapa** — `REQUIRES_NEW` para progresso visível no meio do fluxo.

---

## O que o sistema faz

1. O cliente chama `POST /api/v1/companies/onboard` com os dados de uma empresa,
   seus departamentos (+funcionários), escritórios (+salas) e projetos (+tarefas).
2. A API responde **imediatamente** com `202 Accepted` e um `jobId`.
3. O job roda em background, dividido em **4 etapas** que criam os dados por agregado.
4. O cliente se inscreve em `GET /api/v1/jobs/{id}/stream` (SSE) e recebe eventos
   `progress` entre as etapas, terminando com `completed` ou `failed`.

A frontend estática em `src/main/resources/static/index.html` demonstra o fluxo
com uma barra de progresso animada.

---

## Stack

- **Java 25**, **Spring Boot 4**, **Spring MVC** (SSE funciona melhor em servlet
  clássico do que em WebFlux para este cenário).
- **Postgres 18**, **Flyway** para migrations, **JPA/Hibernate** para persistência.
- **MapStruct** para mapeamento domínio ↔ JPA; **Lombok** apenas nas JPA entities.
- **Virtual Threads** (`spring.threads.virtual.enabled=true`) para o tomcat +
  um executor dedicado para a orquestração.

---

## Como rodar

```bash
# 1. Postgres
docker compose up -d

# 2. App
mvn spring-boot:run

# 3. Abra no browser
# http://localhost:8080
```

Clique **Start onboarding**. O tamanho do payload está no topo do `<script>` em
`index.html` (`SIZE.departments`, etc.) — aumente para ver cada etapa demorar mais.

---

## Clean Architecture

A regra é simples: **as dependências só apontam para dentro**.

```
┌─────────────────────────────────────────────────────────────┐
│  adapter/   (Ring 3)  controllers, presenters, JPA          │
│    ┌────────────────────────────────────────────────────┐   │
│    │  usecase/ (Ring 2)  interactors, gateways (iface)  │   │
│    │    ┌───────────────────────────────────────────┐   │   │
│    │    │  entity/ (Ring 1)  records/classes puras  │   │   │
│    │    └───────────────────────────────────────────┘   │   │
│    └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

- `entity/Company.java`, `entity/OnboardingJob.java`, ... — **não importam
  nada de Spring, JPA ou Jackson**. Se amanhã o projeto virar CLI, essas classes
  continuam funcionando iguais.
- `usecase/` declara **interfaces** (`CompanyGateway`, `OnboardingJobGateway`,
  `ProgressPublisher`) que ele precisa. **Não sabe** que existe Postgres ou SSE.
- `adapter/` implementa essas interfaces usando JPA, HTTP, etc. É o único ring
  que encosta em framework.

### Por que isso vale a pena?

- Troca de tecnologia = trocar o adapter, nada do core muda. Quer trocar Postgres
  por Mongo? Reimplemente `CompanyGateway`. Quer trocar SSE por WebSocket?
  Reimplemente `ProgressPublisher`.
- **Testabilidade**: interactors testam com fakes das interfaces, sem subir
  contexto Spring nem banco.

---

## Patterns aplicados

### 1. Input boundary (interface do use case)

**O quê**: `usecase/onboard/OnboardCompany.java` é só uma interface. O controller
injeta `OnboardCompany`, não `OnboardCompanyInteractor`.

```java
public interface OnboardCompany {
    void execute(OnboardCompanyInput input, OnboardCompanyPresenter presenter);
}
```

**Por quê**: o controller depende de uma abstração do Ring 2. O Ring 2 não conhece
o controller. Inversão de dependência clássica.

---

### 2. Presenter pattern (output boundary)

**O quê**: o use case **não retorna valor**. Ele entrega o `Output` a um
`OnboardCompanyPresenter`, que sabe virar HTTP.

```java
public interface OnboardCompanyPresenter {
    void presentAccepted(OnboardCompanyOutput output);     // 202
    void presentAlreadyExists(OnboardCompanyOutput output); // 200 (replay idempotente)
}
```

**Por quê**: a *mesma* lógica de use case precisa gerar dois status HTTP
diferentes dependendo do resultado. Um `return` único esconderia essa distinção.
O presenter carrega a responsabilidade de view-model para fora do Ring 2, onde
ele não deveria estar.

---

### 3. Gateway pattern

**O quê**: `CompanyGateway` e `OnboardingJobGateway` (interfaces em Ring 2),
implementados por `CompanyGatewayJpa` e `OnboardingJobGatewayJpa` em Ring 3.

**Por quê**: o gateway é modelado em torno do **agregado** (Company é uma
agregação de Department, Employee, Office, Room, Project, Task), não em torno
de tabelas. Se fosse um repositório por tabela (um para Department, outro para
Employee...), o Ring 2 teria que orquestrar detalhes de persistência — quebrando
a fronteira.

---

### 4. Strategy + Template Method para as etapas

**O quê**: cada etapa do onboarding é um bean Spring que estende `CreationStep`:

```java
public abstract class CreationStep {
    public abstract int order();
    public abstract String name();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(CreationStepContext ctx) {
        validate(ctx);   // hook opcional
        doExecute(ctx);  // implementação concreta
    }
}
```

- **Template Method** — o esqueleto do algoritmo (validate → doExecute) é fixo e
  vive na classe-base, com `REQUIRES_NEW` aplicado uma vez só.
- **Strategy** — cada subclasse (`CreateCompanyStep`, `CreateDepartmentsStep`,
  `CreateOfficesStep`, `CreateProjectsStep`) é uma estratégia registrada como bean;
  o interactor injeta `List<CreationStep>` e ordena por `order()`.

**Por quê**: adicionar uma nova etapa é criar um `@Component` com o `order()`
apropriado. O interactor não muda. O número total de etapas (`totalSteps`) sai
naturalmente do `steps.size()`.

---

### 5. Idempotency Key

**O quê**: o controller exige o header `Idempotency-Key`. O interactor consulta
o gateway por esse key antes de criar o job.

```java
var existing = jobGateway.findByIdempotencyKey(input.idempotencyKey());
if (existing.isPresent()) {
    presenter.presentAlreadyExists(...);  // 200
    return;
}
```

**Por quê**: clientes HTTP reais têm retry automático. Sem idempotência, um retry
após um timeout do lado do cliente criaria duas empresas. Com idempotência, o
segundo POST com o mesmo key retorna o mesmo `jobId` e ponto.

---

### 6. Orquestração assíncrona com Virtual Threads

**O quê**: após persistir o job em estado `PENDING`, o interactor submete a
execução real a um executor dedicado (`orchestratorExecutor`) e retorna
imediatamente.

```java
orchestratorExecutor.execute(() -> runSafely(saved.id(), input));
presenter.presentAccepted(...);  // 202, já com jobId
```

O executor é virtual-thread-per-task (`AsyncConfig`):

```java
Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual().name("onboarding-orchestrator-", 0).factory());
```

**Por quê**: jobs de onboarding podem demorar. Segurar a thread HTTP até o fim
seria desperdício e quebraria SLA de resposta. Virtual threads deixam a gente
fazer *fire-and-forget* sem configurar pool-size, backpressure, etc.

---

### 7. Observer / Publisher (progresso em SSE)

**O quê**: o orquestrador publica progresso através de uma interface Ring 2
(`ProgressPublisher`), que **não sabe** que o transporte é SSE.

```java
public interface ProgressPublisher {
    void publish(OnboardingJob job);
}
```

Do lado do adapter, `SseProgressPublisher` traduz o domínio em JSON e joga num
`SseEmitterRegistry`, que mantém um `Map<jobId, List<SseEmitter>>` para fan-out
multi-assinante (duas abas olhando o mesmo job, por exemplo).

**Por quê**: trocar por WebSocket, Kafka, webhooks etc. = só trocar o adapter.
O orquestrador continua chamando `progressPublisher.publish(job)`.

---

### 8. Concorrência: advisory lock + índice único parcial

O sistema precisa garantir: **não existem dois onboardings ativos para a mesma
empresa ao mesmo tempo**. Usamos duas barreiras complementares:

**a) Índice único parcial no Postgres**
```sql
CREATE UNIQUE INDEX uq_onboarding_job_active_company
    ON onboarding_job (company_name)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
```
Se dois POSTs chegarem e tentarem inserir `onboarding_job` para a mesma empresa,
o segundo INSERT falha com violation — o `GlobalExceptionHandler` converte em 409.

**b) Advisory lock transacional**
```java
pg_try_advisory_xact_lock(hashtext('company:' + name))
```
Dentro de `CreateCompanyStep`, antes de gravar a `Company`, pegamos um lock
postgres de escopo transacional. Ele é liberado automaticamente no commit/rollback
do `REQUIRES_NEW`. Isso fecha a janela entre "job criado" e "company gravada".

**Por quê duas barreiras?** O índice único é a garantia dura. O advisory lock
reduz a chance de chegarmos até lá com dois caminhos concorrentes — falha rápido
e com mensagem clara em vez de estourar constraint.

---

### 9. Transações por etapa (`REQUIRES_NEW`)

**O quê**: cada `CreationStep.execute()` roda em sua **própria** transação, commitada
independentemente. Entre etapas, o orquestrador grava o progresso atualizado no
`onboarding_job`.

**Por quê**: assim o progresso é **visível em tempo real**. Se a etapa 3 falhar,
as etapas 1 e 2 já estão commitadas e o job marca FAILED com `failed_step` apontando
o problema. Não é rollback de tudo — é *crash-consistent*, não *atomic*. Para
este caso de uso, progresso parcial visível é melhor que tudo-ou-nada.

---

## Fluxo de uma requisição

```
 Client                    Controller           Interactor           Executor        DB
   │                          │                     │                    │            │
   │─POST /onboard──────────▶│                     │                    │            │
   │                          │─execute(in, pres)─▶│                    │            │
   │                          │                     │─findByIdemKey────────────────▶│
   │                          │                     │◀───empty──────────────────────│
   │                          │                     │─save(job PENDING)────────────▶│   tx1
   │                          │                     │─submit(runSafely)─▶│            │
   │                          │◀─────202 + jobId────│                    │            │
   │◀─202 Accepted──────────│                     │                    │            │
   │                                                │                    │            │
   │─GET /stream──────────▶ JobController           │                    │            │
   │◀─ SSE (initial snap) ───────────────────────── │                    │            │
   │                                                │                    │            │
   │                                                │                    │─run(...)──▶│
   │                                                │             start→ IN_PROGRESS  │   tx2
   │◀─ event:progress ──── SseProgressPublisher ◀── ProgressPublisher ◀──│            │
   │                                                │                    │─step 1 (REQUIRES_NEW)▶│ tx3
   │                                                │                    │─step 2 (REQUIRES_NEW)▶│ tx4
   │◀─ event:progress ─────────────────────────────────── (após cada etapa) ──────────│
   │                                                │                    │─step 3 ...            │
   │                                                │                    │─step 4 ...            │
   │                                                │                    │─complete()────▶│      txN
   │◀─ event:completed ────────────────────────────────────────────────────────────────│
```

---

## Mapa de diretórios

```
src/main/java/com/example/onboarding/
├── CompanyOnboardingApplication.java       — entrypoint
├── entity/                                  — Ring 1 (regra de negócio pura)
│   ├── Company.java, Department.java, Employee.java,
│   ├── Office.java, Room.java, Project.java, Task.java
│   ├── OnboardingJob.java                  — estado do job (mutável de propósito)
│   └── JobStatus.java
├── usecase/                                 — Ring 2 (interactors + boundaries)
│   ├── onboard/
│   │   ├── OnboardCompany.java              — input boundary
│   │   ├── OnboardCompanyInteractor.java    — orquestrador
│   │   ├── OnboardCompanyPresenter.java     — output boundary
│   │   ├── OnboardCompanyInput.java / Output.java
│   │   └── step/                            — Strategy + Template Method
│   │       ├── CreationStep.java (abstract, template)
│   │       ├── CreationStepContext.java
│   │       ├── CreateCompanyStep.java, CreateDepartmentsStep.java,
│   │       └── CreateOfficesStep.java, CreateProjectsStep.java
│   ├── progress/                            — use case read-only
│   │   ├── GetJobProgress.java / Interactor.java / Output.java
│   └── gateway/                             — portas (interfaces)
│       ├── CompanyGateway.java, OnboardingJobGateway.java
│       ├── AdvisoryLockGateway.java, ProgressPublisher.java
│       └── DuplicateCompanyNameException.java
├── adapter/                                 — Ring 3 (implementações)
│   ├── controller/                          — HTTP
│   │   ├── OnboardingController.java, JobController.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── request/OnboardCompanyRequest.java
│   │   └── response/JobAcceptedResponse.java / JobProgressResponse.java
│   ├── presenter/OnboardCompanyJsonPresenter.java
│   └── gateway/persistence/
│       ├── CompanyGatewayJpa.java, OnboardingJobGatewayJpa.java,
│       ├── AdvisoryLockGatewayJpa.java
│       ├── model/  (JPA entities: CompanyJpa, DepartmentJpa, ...)
│       ├── repository/ (Spring Data JpaRepository + AdvisoryLockRepository)
│       └── mapper/ (MapStruct + OnboardingJobJpaMapper manual)
└── infrastructure/                          — wiring/config
    ├── config/AsyncConfig.java              — orchestratorExecutor (virtual threads)
    └── async/
        ├── SseEmitterRegistry.java          — fan-out multi-assinante
        └── SseProgressPublisher.java        — implementa ProgressPublisher

src/main/resources/
├── application.yml
├── db/migration/V1__init.sql                — Flyway
└── static/index.html                         — demo UI

docker-compose.yml                            — Postgres 18
```

---

## Pontos para revisitar / aprofundar depois

- **Retries idempotentes por etapa**: o que acontece se a VM cair no meio do
  step 3? Hoje o job fica em `IN_PROGRESS` para sempre. Um *reaper* scheduled
  que marca jobs órfãos como FAILED seria o próximo passo natural.
- **Saga / compensação**: alternativa a "commits parciais" — cada etapa teria
  uma operação inversa. Vale comparar com o modelo atual em um projeto futuro.
- **CQRS**: o use case read-only `GetJobProgress` já pula o presenter. Um próximo
  projeto poderia separar modelos de leitura/escrita de verdade.
- **Outbox**: publicar eventos de domínio (ex. `CompanyOnboarded`) de forma
  transacional para um broker, em vez de SSE efêmero.
