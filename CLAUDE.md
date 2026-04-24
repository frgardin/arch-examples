# arch-examples — contexto do repositório

## Propósito

Este repositório é um **workspace de estudo**. Cada subpasta é um projeto Spring Boot
independente que exercita um ou mais **padrões de arquitetura** com foco em
aprendizado: Clean Architecture, patterns GoF, estratégias de concorrência,
orquestração assíncrona, mensageria, event sourcing, etc.

O objetivo não é entregar um produto — é **praticar e comparar abordagens**. Por isso:

- Cada projeto é auto-contido (próprio `pom.xml`, `docker-compose.yml`, `README.md`).
- O código prioriza **clareza didática** sobre esperteza. Comentários explicam o
  *porquê* de decisões que não são óbvias pelo código (ex.: por que REQUIRES_NEW,
  por que advisory lock + índice único parcial, etc.).
- Trade-offs que foram considerados mas não aplicados costumam ser comentados também —
  a ideia é que o projeto funcione como referência de estudo depois.

## Projetos atuais

| Pasta | Foco principal |
|---|---|
| `company-onboarding/` | Clean Architecture + job assíncrono com progresso via SSE. Patterns: Strategy+Template Method, Presenter, Gateway, Observer, Idempotency Key, Virtual Threads, Advisory Lock + índice único parcial. Stack: Spring Boot 4, Java 25, Postgres 18, Flyway, JPA, MapStruct. |

Ao adicionar um novo projeto, atualize esta tabela.

## Convenções ao criar/editar projetos

- **Clean Architecture** é o ponto de partida quando faz sentido. A organização
  padrão por pacotes é:
  ```
  entity/      Ring 1  — regras de negócio puras, sem Spring/JPA/Jackson
  usecase/     Ring 2  — interatores, gateways (interfaces), boundaries
  adapter/     Ring 3  — controllers, presenters, gateways JPA, mappers
  infrastructure/ Ring 3/4 — config, executors, SSE registry, etc.
  ```
- Entidades de domínio (Ring 1) são **records** ou classes Java puras. JPA entities
  ficam em `adapter/gateway/persistence/model/` e nunca vazam para Ring 1/2.
- Gateways (Ring 2) são modelados pelo **agregado**, não pela tabela. MapStruct
  converte entre modelo JPA e domínio quando o boilerplate compensa.
- Use case expõe **input boundary** (interface) e **output boundary** (presenter);
  controllers injetam a interface, nunca o interactor concreto.
- Use cases read-only podem pular o presenter (retornar Optional) quando o cerimonial
  completo for desproporcional — mas **marque isso com um comentário**.
- Migrations Flyway em `src/main/resources/db/migration/`. Schema validado por
  Hibernate (`ddl-auto: validate`), nunca gerado.
- Stack-alvo atual: **Java 25, Spring Boot 4, Postgres 18**. Em Spring Boot 4 as
  autoconfigurações foram modularizadas — dependências como `spring-boot-flyway`
  precisam ser declaradas explicitamente (não vêm transitivas do `flyway-core`).

## Como priorizar ao responder

- **Ensinar > entregar**. Quando fizer uma escolha, explique por que em uma
  linha no código ou na resposta.
- Se o usuário pedir "faça mais X" sem contexto, interpretar à luz do intuito de
  estudo: ele quer *observar* o comportamento, então prefira mudanças que tornam
  o padrão mais visível (mais dados, logs, tempo perceptível) em vez de apenas
  fazer funcionar.
- Ao sugerir um padrão novo, relacione explicitamente com patterns já aplicados
  em outros projetos do repo — a ideia é construir um mapa mental comparativo.
