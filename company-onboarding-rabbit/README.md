# company-onboarding-rabbit

Variante do `company-onboarding` onde o despacho do job usa **RabbitMQ** em vez
de executor virtual in-process.

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
| Despacho | `Executor` virtual thread (`orchestratorExecutor.execute(...)`) | `RabbitTemplate.convertAndSend(...)` |
| Consumo | Mesma JVM, closure captura `jobId` + `input` | `@RabbitListener` em outra (ou mesma) JVM |
| Porta Ring 2 | — | `OnboardingJobDispatcher` (nova) |
| Configuração | `infrastructure/config/AsyncConfig.java` | `infrastructure/messaging/RabbitConfig.java` |
| Lógica de orquestração | Dentro de `OnboardCompanyInteractor.run/runSafely` | Extraída para `OnboardingWorker.process` (Ring 2) |
| Durabilidade do trigger | Nenhuma (perde se JVM cair) | Mensagem persistida na queue com DLX |

## Topologia AMQP

```
onboarding.exchange (direct)
    │ routing-key: onboarding.start
    ▼
onboarding.start.queue                       ← x-dead-letter-exchange configurado
    │                                            ↓ em caso de reject sem requeue
    ▼                                        onboarding.dlx (direct)
@RabbitListener (OnboardingJobListener)          ↓ routing-key: onboarding.start.dead
    │                                        onboarding.start.dlq
    ▼
OnboardingWorker.process(jobId, input)
```

## Como rodar

```bash
docker compose up -d        # Postgres 5433, RabbitMQ 5672 + UI 15672
mvn spring-boot:run         # http://localhost:8080
```

- Frontend: http://localhost:8080
- RabbitMQ Management UI: http://localhost:15672 — login `onboarding` / `onboarding`

## Pontos de atenção específicos

- **Não há retry automático**: `default-requeue-rejected: false` em `application.yml`
  manda mensagem rejeitada direto para DLQ. Motivo: steps não são rerun-safe
  (commits parciais na metade de um run se reexecutam criariam duplicatas). Para
  ligar retry, cada step precisaria virar idempotente primeiro.
- **Mensagem carrega o `OnboardCompanyInput` inteiro** para evitar precisar
  re-ler do DB. Payloads muito grandes (MBs) justificariam o pattern *claim
  check* — guardar o payload numa tabela e mandar só o `jobId` na mensagem.
- **Progresso SSE ainda é single-node**: se o worker rodar no nó B e o cliente
  estiver conectado no nó A, A não vê o progresso. Solução: publicar progresso
  num exchange fanout e cada nó repassar para seus emitters locais.
- **Idempotência de reprocessamento**: se a mensagem for redelivered (ack
  perdido, crash do consumer após começar), o worker tenta rodar de novo e o
  `CreateCompanyStep` explode no unique `company.name`. Produção-ready exige
  checar o status do job no início do worker e sair cedo se já estiver
  IN_PROGRESS/COMPLETED.
