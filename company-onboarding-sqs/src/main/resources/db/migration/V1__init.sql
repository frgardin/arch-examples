-- Root aggregate
CREATE TABLE company (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    tax_id      VARCHAR(50)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE department (
    id          UUID PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES company(id),
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (company_id, name)
);
CREATE INDEX idx_department_company ON department(company_id);

CREATE TABLE employee (
    id             UUID PRIMARY KEY,
    department_id  UUID         NOT NULL REFERENCES department(id),
    name           VARCHAR(200) NOT NULL,
    email          VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_employee_department ON employee(department_id);

CREATE TABLE office (
    id          UUID PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES company(id),
    name        VARCHAR(200) NOT NULL,
    city        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_office_company ON office(company_id);

CREATE TABLE room (
    id          UUID PRIMARY KEY,
    office_id   UUID         NOT NULL REFERENCES office(id),
    floor       INTEGER      NOT NULL,
    number      VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_room_office ON room(office_id);

CREATE TABLE project (
    id          UUID PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES company(id),
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_company ON project(company_id);

CREATE TABLE task (
    id           UUID PRIMARY KEY,
    project_id   UUID         NOT NULL REFERENCES project(id),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_project ON task(project_id);

-- Async job tracking
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

-- Race-condition guard: at most one ACTIVE job per company name.
-- A second concurrent onboarding for the same company will fail the unique index,
-- which we translate to 409 at the controller.
CREATE UNIQUE INDEX uq_onboarding_job_active_company
    ON onboarding_job (company_name)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
