# Três formas de despachar um job assíncrono

Este repositório tem **três variantes do mesmo caso de uso** (onboarding de
empresa com progresso em SSE). O núcleo é idêntico — entidades, use cases,
controllers, banco, SSE. **O que muda é só como o job PENDING é entregue ao
worker que vai executar as etapas**.

| Projeto | Mecanismo de despacho |
|---|---|
| `company-onboarding/`       | In-process: virtual thread direta (sem broker) |
| `company-onboarding-rabbit/`| RabbitMQ: exchange + queue + `@RabbitListener` |
| `company-onboarding-sqs/`   | AWS SQS (LocalStack local): polling com AWS SDK v2 |

Todos os três usam **Postgres** como *source of truth* do estado do job. A
comparação **não** é "Postgres vs RabbitMQ vs SQS como banco" — é "Postgres
sozinho vs Postgres + broker vs Postgres + fila gerenciada **para orquestrar
async**".

---

## O que mudou no código entre as variantes

Três peças foram isoladas pra trás de interfaces ou beans Spring, e só elas
diferem:

```
usecase/gateway/OnboardingJobDispatcher.java    ← porta (Ring 2)
    │
    ├─ (company-onboarding)         Executor de virtual thread
    ├─ (company-onboarding-rabbit)  RabbitOnboardingDispatcher  (RabbitTemplate)
    └─ (company-onboarding-sqs)     SqsOnboardingDispatcher     (SqsClient)

usecase/onboard/OnboardingWorker.java           ← lógica de execução, igual nas três
    │
    Invocado por:
    ├─ (company-onboarding)         o próprio executor (fire-and-forget)
    ├─ (company-onboarding-rabbit)  OnboardingJobListener (@RabbitListener)
    └─ (company-onboarding-sqs)     SqsOnboardingListener (SmartLifecycle + long poll)
```

**O `OnboardCompanyInteractor`, os `CreationStep`, os gateways JPA, o SSE — tudo
igual**. É esse o ponto pedagógico: Clean Architecture deixa a troca de
mecanismo acontecer na borda.

---

## Matriz rápida

| Dimensão | In-process (Postgres-only) | RabbitMQ | SQS |
|---|---|---|---|
| **Infra extra necessária** | Nenhuma | Broker RabbitMQ rodando | Conta AWS ou LocalStack |
| **Durabilidade** | A do Postgres (job está salvo; executor é volátil) | Mensagem persistida no broker (quorum/classic queue durável) | Persistência gerenciada pela AWS (replicação multi-AZ) |
| **Entrega** | Exactly-once local ao processo (e perde se a JVM cair) | At-least-once com acks (requeue/DLQ configurável) | At-least-once (visibility timeout + redrive policy) |
| **Modelo** | Push síncrono in-memory | Push: broker entrega, consumer reage | Pull: consumer faz long-poll contra a fila |
| **Latência publish → consume** | Microssegundos (mesma JVM) | ~ms (broker local), ~poucos ms (broker remoto) | ~dezenas a centenas de ms (long-poll 20s, batch) |
| **Throughput** | Limitado pela capacidade local (CPU, conexões DB) | Dezenas de milhares/s por broker, scale-out por cluster | Praticamente ilimitado, escala sozinho (cobrado por msg) |
| **Ordenação** | Ordem de submissão (dentro de 1 JVM) | Por fila; FIFO natural se 1 consumer; com concurrency>1, nenhuma garantia | Standard: best-effort; **FIFO queue**: estrita, por message-group-id |
| **Retry** | Precisa você codar (hoje: só DLQ-mental, sem retry) | Declarativo (requeue, x-dead-letter-exchange, retry topic) | Queue-level RedrivePolicy (maxReceiveCount → DLQ) |
| **DLQ** | Inexistente (falha vira status FAILED no DB) | DLX + queue dedicada | DLQ nativa (outra queue referenciada no RedrivePolicy) |
| **Observabilidade** | Só o que o app loga | Management UI (`:15672`), métricas Prometheus | CloudWatch (msgs em voo, idade da mensagem, DLQ size) |
| **Scale horizontal** | 1 JVM = 1 worker. Vários JVMs → cada um tenta executar seu próprio → race no job | Vários consumers → broker distribui; scale-out direto | Vários consumers → cada um faz receive; scale-out direto |
| **Backpressure** | Explode memória se publicar mais do que consome | Fila enche no broker; pode ter limites e reject-publish | Fila enche na AWS; message-retention até 14 dias |
| **Custo** | Zero marginal | Custo do host do broker (EC2, Docker, Amazon MQ, CloudAMQP) | Pay-per-request (~$0.40 por milhão) + data transfer |
| **Vendor lock** | Nenhum | Portável (AMQP é padrão; RabbitMQ, LavinMQ, etc.) | Alto (SQS-specific) |
| **Complexidade operacional** | Baixa | Média — quorum queues, clustering, memory alarms | Quase zero (serviço gerenciado) |
| **Entre-regiões / multi-datacenter** | N/A (1 máquina) | Federation / shovel (setup manual) | Cross-region replication (nativa, mas cobrada) |

---

## Variante 1: Postgres + in-process (a base)

### Como funciona

```java
// OnboardCompanyInteractor
OnboardingJob saved = jobGateway.save(job);
orchestratorExecutor.execute(() -> runSafely(saved.id(), input));
presenter.presentAccepted(...);
```

A virtual thread herda só o `input` (capturado no closure) e o `jobId`. Quando
termina uma etapa, grava o progresso e publica no SSE.

### Quando escolher

- MVP, protótipo, qualquer coisa **single-node**.
- Jobs curtos (segundos a minutos), onde "a JVM vai cair no meio" é improvável
  e aceitável.
- Você não quer gerenciar broker nenhum.

### Onde quebra

- **JVM cai mid-job** → job fica `IN_PROGRESS` para sempre. Sem reaper, o
  índice único parcial bloqueia novos onboardings daquela empresa.
- **Múltiplos nós**: cada nó processa o que ele mesmo recebeu. Um job
  submetido no nó A **nunca** pode ser continuado pelo nó B se A morrer. Não
  há redistribuição.
- **Backpressure implícito**: se POSTs chegam mais rápido do que o executor
  consome, as virtual threads acumulam. O custo é pequeno (virtual threads
  são baratas), mas não há controle de limite.
- **Observabilidade** termina no log da aplicação.

### Variação possível (não implementada aqui)

**Postgres-as-queue** com `SELECT ... FOR UPDATE SKIP LOCKED`: uma tabela
`job_queue` com os jobs pendentes; workers fazem polling. É um meio-termo —
durabilidade, coordenação entre nós, sem broker extra. Bom para cargas baixas
a médias. Vale um quarto projeto no futuro.

---

## Variante 2: RabbitMQ

### Como funciona

```
Interactor ──► RabbitTemplate.convertAndSend(exchange, rk, msg)
                                 │
                       onboarding.exchange (direct)
                                 │ routing-key: onboarding.start
                                 ▼
                       onboarding.start.queue
                                 │ push (persistent connection)
                                 ▼
                       @RabbitListener(queues = ...) ──► OnboardingWorker.process()
```

Dead-lettering configurado via argumentos da queue:
`x-dead-letter-exchange = onboarding.dlx`. Quando a mensagem é rejected sem
requeue, o broker publica ela no DLX, que bindou a `onboarding.start.dlq`.

### Ganhos vs in-process

- **Job sobrevive a crash de worker**: a mensagem fica na fila até ser
  ack'd. Se o processo morrer mid-run, a mensagem é redelivered para outro
  consumer. (Com a **ressalva** de idempotência abaixo.)
- **Múltiplos nós cooperam**: N instâncias da app, todas registradas na mesma
  queue — o broker faz round-robin de mensagens.
- **Retry declarativo**: com `spring.rabbitmq.listener.simple.retry.enabled`
  ligado, Spring AMQP faz retry com backoff antes de DLQ. Aqui desliguei
  porque os steps NÃO são rerun-safe (commits parciais). Retry requer
  idempotência por step.
- **Management UI e métricas**: `:15672` mostra filas, taxas, consumers.

### Cuidados

- **Idempotência de processamento**: se a mensagem for redelivered (ack perdido,
  consumer crashou), o worker pode começar de novo a partir da etapa 1. O
  `CreateCompanyStep` tenta inserir `Company` de novo → violação de unique.
  Hoje o job seria marcado FAILED e a mensagem vira DLQ. **Correção real**:
  checar o status do job ao entrar no worker e, se já estiver IN_PROGRESS/
  COMPLETED, pular.
- **Mensagens grandes**: `OnboardingJobMessage` carrega o `OnboardCompanyInput`
  inteiro, que pode ter centenas de KB. AMQP aguenta mas impacta a memória do
  broker. Para payloads realmente grandes, usar o pattern **claim check**:
  persistir o payload no DB e mandar só `jobId`.
- **Cluster operation**: quorum queues são o default moderno (raft); classic
  mirrored queues estão depreciadas. Setup correto exige entender esses detalhes.
- **Progresso SSE ainda é single-node**. Se o cliente está no nó A e o worker
  roda no B, o `SseEmitterRegistry` de A não recebe. Próximo passo natural:
  publicar progresso num fanout exchange que todos os nós consomem, e cada
  nó propaga para seus emitters locais.

### Quando escolher

- Você precisa de controle fino: retries, DLQ, roteamento (fanout, topic,
  headers), priority queues, delayed message plugin.
- Latência baixa entre publish e consume importa.
- Você pode rodar infra própria (Kubernetes operator, Amazon MQ, CloudAMQP).
- Precisa de protocolo aberto (AMQP 0.9.1, AMQP 1.0, MQTT, STOMP).

### Onde quebra

- **Operação**: broker é um serviço stateful. Tem upgrade, failover, memory
  alarm, disk pressure, particionamento de rede.
- **Custo fixo**: paga pelo broker estar de pé, não pelo uso.

---

## Variante 3: AWS SQS

### Como funciona

```
Interactor ──► SqsClient.sendMessage(queueUrl, body)
                                 │
                                 ▼
                 SQS queue (onboarding-start-queue)
                 └─ RedrivePolicy: maxReceiveCount=3 → onboarding-start-dlq
                                 │
                                 ▼ receiveMessage (long-poll)
                     SqsOnboardingListener (polling loop em 2 threads)
                                 │
                                 ├─ sucesso → deleteMessage
                                 └─ exceção → (nada) → SQS redelivera após VisibilityTimeout
```

Diferente do RabbitMQ, o consumer **puxa** a mensagem — não há conexão
persistente. Long-poll de 20s faz a chamada ficar pendurada até aparecer uma
mensagem (ou timeout), então custa quase nada em termos de chamada vazia.

### Ganhos vs RabbitMQ

- **Zero operação**: fila é um recurso AWS. Sem patch, sem upgrade, sem memória
  enchendo.
- **Escala sozinho**: não existe "capacidade" de uma queue SQS — a AWS absorve
  até cotas gigantes.
- **DLQ + retention**: built-in. Mensagens podem ficar até 14 dias na fila.
- **Integra nativamente** com outros serviços AWS (Lambda triggers, EventBridge
  pipes, SNS fanout).

### Cuidados

- **At-least-once, sempre**. Mesmo que você delete a mensagem, a AWS pode
  ter feito delivery duplicado para outro consumer antes. FIFO queues mitigam
  (exactly-once com deduplication ID), mas têm throughput limitado.
- **VisibilityTimeout** é crítico. Se o worker demora mais do que o timeout
  (300s aqui), outro consumer pega a mesma mensagem e começa a processar em
  paralelo. Solução: chamar `ChangeMessageVisibility` periodicamente durante
  jobs longos, ou aumentar o timeout.
- **Polling custa dinheiro** (cobrado por request mesmo quando vazio). Por isso
  long-poll de 20s, não busy-loop.
- **Sem routing** nativo (só filas). Para fan-out ou topic-like, combina com
  SNS ou EventBridge.
- **Vendor lock**: migrar para outro broker = reescrever dispatcher + listener
  + configuração de infra.
- Mesmos cuidados de idempotência do RabbitMQ valem aqui.

### Quando escolher

- Você já está em AWS. A integração com IAM, VPC endpoints, CloudWatch, Lambda
  fica "de graça".
- Você NÃO quer operar broker.
- Cargas variáveis / bursty — SQS absorve tudo sem capacity planning.
- Retenção longa (até 14 dias) é útil (ex. consumer offline por manutenção).

### Onde quebra

- Workloads de latência abaixo de ~100ms (polling + batch impõe piso).
- Ordenação estrita cross-group (FIFO é só por group-id).
- Projetos multi-cloud ou on-prem.

---

## Quando escolher cada um — regra prática

1. **Single-node, jobs curtos, MVP**: in-process + Postgres.
2. **Multi-node, quer controle fino, infra própria ou Kubernetes**: RabbitMQ.
3. **Rodando na AWS e quer zero operação**: SQS.
4. **Quer durabilidade e coordenação multi-node SEM introduzir broker**:
   Postgres-as-queue (`SELECT FOR UPDATE SKIP LOCKED`). Não está neste repo
   mas é uma quarta alternativa que vale praticar.

---

## O que continua igual em todas as variantes

- **Idempotency key** na entrada.
- **Partial unique index + advisory lock** para "um job ativo por empresa".
- **Optimistic locking** (`@Version`) em `onboarding_job`.
- **Steps como beans Strategy** com `REQUIRES_NEW`.
- **Presenter pattern** no Ring 2.
- **SSE** para progresso — mas com o **mesmo limite**: single-node. Ficou fora
  do escopo desta comparação; veja `company-onboarding/APROFUNDAMENTO.md §5.4`.

---

## O que mudou no modelo mental

No in-process, o orquestrador é **dono do ciclo de vida**: ele decide quando
rodar, executa, marca resultado, tudo num único fluxo de controle.

Com broker/queue, o orquestrador vira **publicador de intenção**: ele diz
"quero que este job seja processado" e desiste do controle. Um **outro ator**
(o listener) é que decide quando e como. Isso abre espaço para:

- **Escalabilidade horizontal** (mais consumers = mais throughput).
- **Resiliência** (consumer caiu → mensagem redelivered).
- **Desacoplamento temporal** (mensagem pode esperar consumer subir).

Em troca, você paga:

- **Complexidade operacional** (broker é mais uma coisa que pode quebrar).
- **Dupla escrita** (job no DB + mensagem no broker → precisa pensar em
  consistência; pattern: **transactional outbox**).
- **Idempotência obrigatória** (redelivery é o caso comum, não raro).

Essa é a ideia central de sistemas distribuídos: você troca simplicidade de
raciocínio por robustez operacional. Todos os três projetos existem pra te
dar um laboratório comparável dos três lados dessa escolha.

---

## Rodando cada variante

Portas são diferentes entre os `docker-compose.yml` pra poder rodar as três
em paralelo sem conflito.

```bash
# Variante 1
cd company-onboarding
docker compose up -d        # Postgres 5432
mvn spring-boot:run         # http://localhost:8080

# Variante 2
cd company-onboarding-rabbit
docker compose up -d        # Postgres 5433, RabbitMQ 5672 + UI 15672
mvn spring-boot:run         # http://localhost:8080
#  Management UI: http://localhost:15672  (onboarding / onboarding)

# Variante 3
cd company-onboarding-sqs
docker compose up -d        # Postgres 5434, LocalStack 4566
mvn spring-boot:run         # http://localhost:8080
#  Listar filas:   aws --endpoint-url=http://localhost:4566 sqs list-queues
#  Ver mensagens:  aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url ...
```

> Aviso: rodar as três ao mesmo tempo na porta 8080 colide. Troque `server.port`
> em duas delas se quiser tudo vivo simultaneamente.
