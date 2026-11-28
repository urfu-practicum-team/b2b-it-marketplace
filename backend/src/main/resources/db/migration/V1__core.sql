-- =====================================================================
-- V1 — ЯДРО
-- Регистрация, профили, портфолио, заказы.
-- PostgreSQL 14+ (gen_random_uuid() встроен с 13). Формат имён — Flyway.
--
-- Договорённости для всех миграций:
--   * PK — UUID; деньги — NUMERIC(14,2), валюта RUB; время — TIMESTAMPTZ (UTC);
--   * перечисления — VARCHAR + CHECK (удобно для @Enumerated(EnumType.STRING));
--   * реальные персональные данные запрещены уставом: номер паспорта не хранится.
-- =====================================================================

CREATE TABLE users (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    role               VARCHAR(16)  NOT NULL
                       CHECK (role IN ('CUSTOMER', 'EXECUTOR', 'ADMIN')),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
    full_name          VARCHAR(200) NOT NULL,
    phone              VARCHAR(32),
    email_confirmed_at TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE customer_profiles (
    user_id             UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    job_title           VARCHAR(120),                 -- CTO, PM, ...
    company_name        VARCHAR(255),
    company_inn         VARCHAR(12)
                        CHECK (company_inn IS NULL OR company_inn ~ '^[0-9]{10}([0-9]{2})?$'),
    company_website     VARCHAR(255),
    company_description TEXT
);

CREATE TABLE executor_profiles (
    user_id             UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name        VARCHAR(120)  NOT NULL,
    headline            VARCHAR(200),
    bio                 TEXT,
    hourly_rate         NUMERIC(10,2) CHECK (hourly_rate IS NULL OR hourly_rate >= 0),
    experience_years    SMALLINT      CHECK (experience_years IS NULL OR experience_years BETWEEN 0 AND 60),
    is_student          BOOLEAN       NOT NULL DEFAULT FALSE,
    university          VARCHAR(200),
    availability        VARCHAR(16)   NOT NULL DEFAULT 'AVAILABLE'
                        CHECK (availability IN ('AVAILABLE', 'BUSY', 'UNAVAILABLE')),
    verification_status VARCHAR(16)   NOT NULL DEFAULT 'NOT_VERIFIED'
                        CHECK (verification_status IN ('NOT_VERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')),
    verified_at         TIMESTAMPTZ,
    -- денормализованные счётчики: обновляются сервисом при завершении сделки / новом отзыве
    rating_avg          NUMERIC(3,2)  NOT NULL DEFAULT 0 CHECK (rating_avg BETWEEN 0 AND 5),
    rating_count        INTEGER       NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    completed_deals     INTEGER       NOT NULL DEFAULT 0 CHECK (completed_deals >= 0),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_executor_search ON executor_profiles (verification_status, rating_avg DESC);

CREATE TABLE skills (
    id       SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(80) NOT NULL UNIQUE,
    category VARCHAR(64) NOT NULL
);

CREATE TABLE executor_skills (
    executor_id      UUID     NOT NULL REFERENCES executor_profiles (user_id) ON DELETE CASCADE,
    skill_id         SMALLINT NOT NULL REFERENCES skills (id),
    level            VARCHAR(16) NOT NULL DEFAULT 'MIDDLE'
                     CHECK (level IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    years_experience SMALLINT CHECK (years_experience IS NULL OR years_experience >= 0),
    PRIMARY KEY (executor_id, skill_id)
);
CREATE INDEX ix_executor_skills_skill ON executor_skills (skill_id);

CREATE TABLE portfolio_items (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    executor_id    UUID         NOT NULL REFERENCES executor_profiles (user_id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    project_url    VARCHAR(500),
    completed_year SMALLINT     CHECK (completed_year IS NULL OR completed_year BETWEEN 1990 AND 2100),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_portfolio_executor ON portfolio_items (executor_id);

-- Файлы: привязка к работе портфолио — прямо здесь (portfolio_item_id).
-- Привязка к сдаче результата (submission_id) добавляется в V2.
CREATE TABLE files (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID         NOT NULL REFERENCES users (id),
    portfolio_item_id UUID         REFERENCES portfolio_items (id) ON DELETE CASCADE,
    storage_key       VARCHAR(512) NOT NULL UNIQUE,
    original_name     VARCHAR(255) NOT NULL,
    content_type      VARCHAR(127) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256            CHAR(64),
    purpose           VARCHAR(24)  NOT NULL
                      CHECK (purpose IN ('AVATAR', 'PASSPORT', 'INN_DOC', 'PORTFOLIO',
                                         'DELIVERABLE', 'CHAT_ATTACHMENT')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_files_owner     ON files (owner_id);
CREATE INDEX ix_files_portfolio ON files (portfolio_item_id) WHERE portfolio_item_id IS NOT NULL;

CREATE TABLE orders (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID          NOT NULL REFERENCES customer_profiles (user_id),
    title        VARCHAR(200)  NOT NULL,
    description  TEXT          NOT NULL,
    category     VARCHAR(64)   NOT NULL,
    budget_min   NUMERIC(14,2) CHECK (budget_min IS NULL OR budget_min >= 0),
    budget_max   NUMERIC(14,2) CHECK (budget_max IS NULL OR budget_max >= 0),
    deadline_at  DATE,
    nda_required BOOLEAN       NOT NULL DEFAULT FALSE,
    status       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT', 'PUBLISHED', 'IN_PROGRESS',
                                   'COMPLETED', 'CANCELLED', 'BLOCKED')),
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
);
CREATE INDEX ix_orders_feed     ON orders (status, published_at DESC);
CREATE INDEX ix_orders_customer ON orders (customer_id, status);

CREATE TABLE order_skills (
    order_id UUID     NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    skill_id SMALLINT NOT NULL REFERENCES skills (id),
    PRIMARY KEY (order_id, skill_id)
);
CREATE INDEX ix_order_skills_skill ON order_skills (skill_id);

-- Настройки платформы (ключ → значение). Сейчас хранит ставку комиссии;
-- вместо отдельной таблицы с историей ставок. Диапазон 10–15 % — критерий приёмки №3.
CREATE TABLE platform_settings (
    key        VARCHAR(64)  PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_by UUID         REFERENCES users (id),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (CASE WHEN key = 'commission_rate_percent'
                THEN value::numeric BETWEEN 10 AND 15
                ELSE TRUE END)
);

COMMENT ON TABLE users              IS 'Учётные записи (заказчик, исполнитель, админ)';
COMMENT ON TABLE customer_profiles  IS 'Профиль заказчика (CTO/PM) и данные его компании';
COMMENT ON TABLE executor_profiles  IS 'Профиль исполнителя, статус верификации, рейтинг';
COMMENT ON TABLE skills             IS 'Справочник навыков/технологий';
COMMENT ON TABLE executor_skills    IS 'Навыки исполнителя (M:N)';
COMMENT ON TABLE portfolio_items    IS 'Работы в портфолио исполнителя';
COMMENT ON TABLE files              IS 'Метаданные загруженных файлов';
COMMENT ON TABLE orders             IS 'Заказы заказчиков';
COMMENT ON TABLE order_skills       IS 'Требуемые навыки заказа (M:N)';
COMMENT ON TABLE platform_settings  IS 'Настройки платформы (ставка комиссии 10–15 %)';
