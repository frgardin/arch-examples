# company-onboarding-sqs

Variante do `company-onboarding` onde o despacho do job usa **AWS SQS** (com
**LocalStack** para rodar localmente) em vez de executor virtual in-process.

**Para conceitos gerais** (Clean Architecture, idempotency key, SSE, steps,
partial unique index, advisory lock): veja o [README do projeto base](../company-onboarding/README.md)
e o [APROFUNDAMENTO](../company-onboarding/APROFUNDAMENTO.md). São idênticos em
todas as variantes.

**Para o diff entre as três variantes e quando escolher cada uma**: veja
[`../ASYNC_COMPARISON.md`](../ASYNC_COMPARISON.md).

Este README cobre **só o que muda nesta variante**.

---

## O que é diferente

| | Base (`company-onboarding`) | Esta variante |
|---|---|---|
| Despacho | `Executor.execute(...)` | `SqsClient.sendMessage(...)` (AWS SDK v2) |
| Consumo | Mesma JVM, closure | Long-poll com `ReceiveMessage` em threads dedicadas |
| Modelo | Push síncrono | **Pull**: consumer faz long-poll (diferente do Rabbit!) |
| Porta Ring 2 | — | `OnboardingJobDispatcher` |
| Configuração | `AsyncConfig.java` | `SqsConfig.java` + `SqsProperties.java` (@ConfigurationProperties) |
| Lógica de orquestração | Dentro de `OnboardCompanyInteractor.run/runSafely` | Extraída para `OnboardingWorker.process` (Ring 2) |
| Durabilidade | Perde se JVM cair | Gerenciada pela AWS (ou pelo LocalStack) |
| DLQ | — | Nativa via RedrivePolicy (`maxReceiveCount=3`) |

## Pilha local

```
┌─────────────────────────────┐     ┌──────────────────────────────┐
│ App (Java 25, Spring Boot)  │     │ LocalStack (:4566)           │
│  ├─ SqsOnboardingDispatcher │──►  │  onboarding-start-queue      │
│  └─ SqsOnboardingListener   │◄──  │      ↓ RedrivePolicy (3x)    │
│        (SmartLifecycle)     │     │  onboarding-start-dlq        │
└─────────────────────────────┘     └──────────────────────────────┘
           │
           ▼
   Postgres 18 (:5434)  ← estado do job, migrations Flyway
```

As filas são criadas automaticamente pelo script
`localstack-init/01-create-queues.sh` quando o container do LocalStack fica
pronto.

## Como rodar

```bash
docker compose up -d        # Postgres 5434, LocalStack 4566
mvn spring-boot:run         # http://localhost:8080
```

Comandos úteis de inspeção (precisa do AWS CLI instalado):

```bash
# Listar filas
aws --endpoint-url=http://localhost:4566 sqs list-queues

# Ver mensagens sem deletar
aws --endpoint-url=http://localhost:4566 sqs receive-message \
    --queue-url http://localhost:4566/000000000000/onboarding-start-queue \
    --max-number-of-messages 10

# Ver DLQ
aws --endpoint-url=http://localhost:4566 sqs receive-message \
    --queue-url http://localhost:4566/000000000000/onboarding-start-dlq \
    --max-number-of-messages 10
```

## Pontos de atenção específicos

- **At-least-once, sempre**. Duplicatas são o caso comum — mesmo com o
  `deleteMessage` bem-sucedido, pode ter havido delivery paralelo antes. FIFO
  queues oferecem dedup via `MessageGroupId` mas limitam throughput. Para
  exactly-once semântico, idempotência na aplicação é obrigatória.

- **VisibilityTimeout = 300s** (script de init). Se a execução passar disso,
  outro consumer pega a mesma mensagem e começa em paralelo. Solução para jobs
  longos: chamar `ChangeMessageVisibility` periodicamente (não implementado
  aqui) ou aumentar o timeout.

- **Long-poll de 20s** (máximo SQS) para minimizar custo por `ReceiveMessage`
  vazio. Short-poll (WaitTimeSeconds=0) em loop seria caro em AWS real.

- **Sem routing nativo**. SQS só entende fila. Fan-out pede SNS na frente ou
  EventBridge.

- **Vendor lock**. Trocar para outro transporte = reimplementar
  `OnboardingJobDispatcher` e o listener. A arquitetura do projeto isola
  essa troca, mas o trabalho existe.

- **LocalStack ≠ AWS real**. LocalStack simula a API do SQS bem o suficiente
  para aprender, mas há diferenças em quotas, latências, consistência. Não use
  como prova de correção em produção.

- **Credenciais hardcoded** em `application.yml` (`test`/`test`) só existem
  para LocalStack. Em AWS real, deixe `access-key`/`secret-key`/`endpoint`
  vazios — o SDK usa a cadeia padrão (env vars, IAM role, `~/.aws/config`).
