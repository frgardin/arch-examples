# Diagramas de arquitetura — as três variantes

Este documento apresenta, em diagramas Mermaid, a visão de alto nível das três
variantes do onboarding e o que muda entre elas. Para trade-offs detalhados
veja [`ASYNC_COMPARISON.md`](ASYNC_COMPARISON.md).

---

## Fluxo comum (idêntico nas três variantes)

Todo o *happy path* visto do cliente é o mesmo. A diferença está na seta
`dispatch` — quem entrega o trabalho ao worker muda de variante para variante.

```mermaid
sequenceDiagram
    actor Client
    participant Ctrl as OnboardingController
    participant Int as OnboardCompanyInteractor
    participant DB as Postgres
    participant Disp as OnboardingJobDispatcher<br/>(porta Ring 2)
    participant W as OnboardingWorker
    participant SSE as SseEmitterRegistry

    Client->>Ctrl: POST /api/v1/companies/onboard<br/>Idempotency-Key + payload
    Ctrl->>Int: execute(input, presenter)
    Int->>DB: findByIdempotencyKey
    DB-->>Int: empty
    Int->>DB: save(OnboardingJob PENDING)
    Note over DB: partial unique index<br/>1 job ativo por empresa
    Int->>Disp: dispatch(jobId, input)
    Int-->>Ctrl: 202 Accepted + jobId
    Ctrl-->>Client: 202 + jobId

    Client->>Ctrl: GET /api/v1/jobs/{id}/stream
    Ctrl->>SSE: register(emitter)

    Disp-->>W: (entrega assíncrona)

    loop para cada step
        W->>DB: step.execute (REQUIRES_NEW)
        W->>DB: save(progress)
        W->>SSE: publish(progress)
        SSE-->>Client: SSE event: progress
    end

    W->>DB: save(COMPLETED)
    W->>SSE: publish(completed)
    SSE-->>Client: SSE event: completed
```

---

## Variante 1 — In-process (Postgres + Virtual Thread)

```mermaid
flowchart LR
    Client(["🧑 Client"])

    subgraph JVM["JVM (Spring Boot)"]
        direction TB
        Ctrl["OnboardingController"]
        Int["OnboardCompanyInteractor"]
        Exec["Virtual Thread Executor<br/>(orchestratorExecutor)"]
        W["OnboardingWorker"]
        Steps["Creation Steps<br/>(Strategy + Template Method)"]
        SSE["SseEmitterRegistry"]
    end

    DB[("🗄️ Postgres 18<br/>— onboarding_job<br/>— company + aggregates")]

    Client -- "POST /onboard" --> Ctrl
    Ctrl --> Int
    Int -- "save(job PENDING)" --> DB
    Int -- "execute(() -> ...)" --> Exec
    Exec --> W
    W --> Steps
    Steps -- "REQUIRES_NEW tx" --> DB
    W -- "save(progress)" --> DB
    W -- "publish(job)" --> SSE
    Client -. "GET /stream (SSE)" .-> SSE
    SSE -. "event: progress/completed" .-> Client

    style Exec fill:#fff4b3,stroke:#c59b00
    style DB fill:#cfe8ff,stroke:#2a6fb0
```

**O que caracteriza esta variante**
- Despacho = **closure numa virtual thread**. Mesma JVM, mesma memória.
- Fila de trabalho não existe fora do JVM — se o processo cai, o *trigger*
  some. O job fica `IN_PROGRESS` no DB e precisaria de um reaper.
- Infra extra: zero. Só Postgres.

---

## Variante 2 — RabbitMQ

```mermaid
flowchart LR
    Client(["🧑 Client"])

    subgraph App["JVM (produtor + consumidor na mesma app)"]
        direction TB
        Ctrl["OnboardingController"]
        Int["OnboardCompanyInteractor"]
        Disp["RabbitOnboardingDispatcher<br/>(RabbitTemplate)"]
        Lst["OnboardingJobListener<br/>@RabbitListener"]
        W["OnboardingWorker"]
        Steps["Creation Steps"]
        SSE["SseEmitterRegistry"]
    end

    subgraph RMQ["🐰 RabbitMQ"]
        direction TB
        Ex[["onboarding.exchange<br/>(direct)"]]
        Q[/"onboarding.start.queue"/]
        DLX[["onboarding.dlx"]]
        DLQ[/"onboarding.start.dlq"/]
    end

    DB[("🗄️ Postgres 18")]

    Client -- "POST /onboard" --> Ctrl
    Ctrl --> Int
    Int -- "save(job PENDING)" --> DB
    Int --> Disp
    Disp -- "publish JSON msg<br/>routing-key: onboarding.start" --> Ex
    Ex --> Q
    Q -. "push (persistent connection)" .-> Lst
    Lst --> W
    W --> Steps
    Steps --> DB
    W --> SSE
    SSE -. "SSE" .-> Client

    Q -. "reject sem requeue" .-> DLX
    DLX --> DLQ

    style RMQ fill:#ffe5c7,stroke:#d08000
    style DB fill:#cfe8ff,stroke:#2a6fb0
```

**O que caracteriza esta variante**
- Despacho = **mensagem JSON num exchange**. Broker persiste; mesmo se o
  consumer cair, a mensagem fica.
- Push-based: o broker **entrega** para consumers conectados.
- Dead Letter Exchange configurado na queue para poison messages.
- Escala horizontal: basta subir mais instâncias — todas se registram na
  mesma queue e o broker distribui (round-robin com prefetch).

---

## Variante 3 — AWS SQS (LocalStack local)

```mermaid
flowchart LR
    Client(["🧑 Client"])

    subgraph App["JVM (produtor + consumidor)"]
        direction TB
        Ctrl["OnboardingController"]
        Int["OnboardCompanyInteractor"]
        Disp["SqsOnboardingDispatcher<br/>(AWS SDK v2 SqsClient)"]
        Poll["SqsOnboardingListener<br/>SmartLifecycle + long-poll<br/>(2 threads)"]
        W["OnboardingWorker"]
        Steps["Creation Steps"]
        SSE["SseEmitterRegistry"]
    end

    subgraph AWS["☁️ SQS (LocalStack ou AWS)"]
        direction TB
        MQ[/"onboarding-start-queue<br/>VisibilityTimeout=300s"/]
        DLQ[/"onboarding-start-dlq"/]
    end

    DB[("🗄️ Postgres 18")]

    Client -- "POST /onboard" --> Ctrl
    Ctrl --> Int
    Int -- "save(job PENDING)" --> DB
    Int --> Disp
    Disp -- "sendMessage(JSON body)" --> MQ
    Poll -- "receiveMessage (20s long-poll)" --> MQ
    MQ -. "messages" .-> Poll
    Poll --> W
    Poll -- "deleteMessage<br/>(ack explícito)" --> MQ
    W --> Steps
    Steps --> DB
    W --> SSE
    SSE -. "SSE" .-> Client

    MQ -- "RedrivePolicy<br/>maxReceiveCount=3" --> DLQ

    style AWS fill:#fff2cc,stroke:#b38f00
    style DB fill:#cfe8ff,stroke:#2a6fb0
```

**O que caracteriza esta variante**
- Despacho = **mensagem numa fila gerenciada**. Sem servidor próprio.
- **Pull-based**: threads dedicadas fazem long-poll (20s, o máximo) contra
  `ReceiveMessage`. Zero mensagens na fila → zero custo significativo.
- Ack explícito via `DeleteMessage`. Sem delete = redelivery após
  VisibilityTimeout. `RedrivePolicy` move pra DLQ após 3 tentativas.

---

## O que muda entre as variantes

A Clean Architecture isola a variação exatamente em **uma porta Ring 2 e uma
caixa Ring 3**. O diagrama abaixo mostra isso:

```mermaid
flowchart TB
    subgraph Ring1["Ring 1 — Entity (idêntico nas 3)"]
        E["Company, Department, Employee,<br/>Office, Room, Project, Task<br/>OnboardingJob, JobStatus"]
    end

    subgraph Ring2["Ring 2 — Use Case (idêntico nas 3)"]
        UC["OnboardCompanyInteractor<br/>OnboardingWorker<br/>CreationStep (Strategy + Template)"]
        Ports["Portas:<br/>CompanyGateway<br/>OnboardingJobGateway<br/>ProgressPublisher<br/>OnboardingJobDispatcher ⭐"]
    end

    subgraph Ring3Same["Ring 3 — Comum às 3 variantes"]
        SSE["SseEmitterRegistry<br/>SseProgressPublisher"]
        JPA["CompanyGatewayJpa<br/>OnboardingJobGatewayJpa<br/>AdvisoryLockGatewayJpa"]
        HTTP["OnboardingController<br/>JobController<br/>OnboardCompanyJsonPresenter"]
    end

    subgraph Ring3Var["Ring 3 — Específico por variante ⭐"]
        direction LR
        V1["In-process:<br/>AsyncConfig<br/>(virtual thread executor)"]
        V2["RabbitMQ:<br/>RabbitConfig<br/>RabbitOnboardingDispatcher<br/>OnboardingJobListener"]
        V3["SQS:<br/>SqsConfig + SqsProperties<br/>SqsOnboardingDispatcher<br/>SqsOnboardingListener"]
    end

    UC --> Ports
    Ports -. "implementado por" .-> JPA
    Ports -. "implementado por" .-> SSE
    Ports -. "implementado por" .-> Ring3Var
    HTTP --> UC

    style Ports fill:#fff4b3,stroke:#c59b00
    style Ring3Var fill:#ffe0e0,stroke:#b04040
```

⭐ = ponto de variação. A única porta Ring 2 que tem implementações diferentes
entre os três projetos é `OnboardingJobDispatcher`. Tudo o mais é código
idêntico.

---

## Tabela comparativa

| Dimensão | **Variante 1**<br/>In-process + Postgres | **Variante 2**<br/>RabbitMQ | **Variante 3**<br/>SQS |
|---|---|---|---|
| **Modelo** | Fire-and-forget em virtual thread | Push (broker entrega) | Pull (consumer faz long-poll) |
| **Infra extra** | Nenhuma | Broker RabbitMQ | Conta AWS ou LocalStack |
| **Durabilidade do trigger** | ❌ Perde se a JVM cair | ✅ Broker persiste | ✅ AWS persiste (replicação multi-AZ) |
| **Entrega** | Exactly-once local | At-least-once + acks | At-least-once + visibility timeout |
| **Retry automático** | ❌ Não | ⚠️ Configurável (desligado aqui) | ✅ Built-in via RedrivePolicy |
| **DLQ** | ❌ Inexistente | ✅ Dead Letter Exchange | ✅ Nativa |
| **Latência dispatch → execução** | µs (mesma JVM) | ms (push) | dezenas a centenas de ms (polling batch) |
| **Throughput máximo** | Limitado por 1 JVM | Dezenas de milhares/s por broker | Praticamente ilimitado |
| **Scale horizontal** | ❌ Cada JVM só processa o que recebeu | ✅ N consumers, broker distribui | ✅ N consumers, fila distribui |
| **Ordenação** | Ordem de submissão (1 JVM) | Por fila; nenhuma com concurrency>1 | Standard: best-effort / FIFO: estrita por group-id |
| **Observabilidade** | Logs da app | Management UI (:15672), métricas Prometheus | CloudWatch nativo |
| **Backpressure** | ❌ Só a memória | ✅ Broker enche, pode rejeitar | ✅ Retém até 14 dias |
| **Ordem de grandeza do custo** | $0 marginal | $-$$ (hospedar broker) | $-$$ (por milhão de msgs) |
| **Vendor lock** | Nenhum | Baixo (AMQP é padrão) | Alto (SQS-específico) |
| **Complexidade operacional** | Baixíssima | Média (cluster, upgrades, disk, memory alarms) | Quase zero (gerenciado) |
| **Multi-região** | N/A | Federation / shovel (setup manual) | Cross-region replication nativa |
| **Quando usar** | MVP, single-node, jobs curtos | On-prem/k8s, controle fino, latência baixa | AWS-native, zero-ops, cargas bursty |
| **Onde quebra** | Crash da JVM = job órfão | Operação do broker | At-least-once força idempotência |

**Legenda**: ✅ ponto forte · ⚠️ depende de configuração · ❌ ausente ou problemático

---

## Conclusão em uma frase

> **A arquitetura Clean Architecture permitiu que, com a mesma base de Ring 1
> e Ring 2, trocar o mecanismo de despacho assíncrono se resumisse a uma porta
> (`OnboardingJobDispatcher`) e seus adaptadores — nada além disso muda entre
> as três variantes.**

Para trade-offs aprofundados, leia
[`ASYNC_COMPARISON.md`](ASYNC_COMPARISON.md). Para os patterns aplicados no
código (Strategy, Template Method, Observer, Presenter, etc.) e como cada um
funciona por baixo, leia
[`company-onboarding/APROFUNDAMENTO.md`](company-onboarding/APROFUNDAMENTO.md).
