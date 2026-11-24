-- =====================================================================
-- V2 — СДЕЛКИ (веха «Маркетплейс: сценарии сделок», 29.11.2026)
-- Отклики, сделка, NDA, сдача результата, чат, уведомления.
-- Текст NDA-шаблона хранится в ресурсах приложения (nda_template.txt),
-- а не в БД; в БД сохраняется «снимок» текста по конкретной сделке.
-- =====================================================================

CREATE TABLE proposals (                       -- отклик исполнителя на заказ
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    executor_id   UUID          NOT NULL REFERENCES executor_profiles (user_id),
    cover_letter  TEXT          NOT NULL,
    price         NUMERIC(14,2) NOT NULL CHECK (price > 0),
    duration_days SMALLINT      NOT NULL CHECK (duration_days > 0),
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (order_id, executor_id)             -- один отклик на заказ
);
CREATE INDEX ix_proposals_executor ON proposals (executor_id, status);

CREATE TABLE deals (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID          NOT NULL REFERENCES orders (id),
    proposal_id       UUID          NOT NULL UNIQUE REFERENCES proposals (id),
    customer_id       UUID          NOT NULL REFERENCES customer_profiles (user_id),
    executor_id       UUID          NOT NULL REFERENCES executor_profiles (user_id),
    amount            NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency          CHAR(3)       NOT NULL DEFAULT 'RUB' CHECK (currency = 'RUB'),
    -- ставка фиксируется при создании сделки: смена настройки на неё не влияет
    commission_rate   NUMERIC(4,2)  NOT NULL CHECK (commission_rate BETWEEN 10 AND 15),
    commission_amount NUMERIC(14,2) GENERATED ALWAYS AS (round(amount * commission_rate / 100, 2)) STORED,
    executor_payout   NUMERIC(14,2) GENERATED ALWAYS AS (amount - round(amount * commission_rate / 100, 2)) STORED,
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING_FUNDING'
                      CHECK (status IN ('PENDING_FUNDING', 'IN_PROGRESS', 'IN_REVIEW',
                                        'ACCEPTED', 'COMPLETED', 'DISPUTED',
                                        'CANCELLED', 'REFUNDED')),
    deadline_at       DATE,
    funded_at         TIMESTAMPTZ,            -- деньги удержаны в эскроу
    accepted_at       TIMESTAMPTZ,            -- результат принят заказчиком
    completed_at      TIMESTAMPTZ,            -- выплата завершена
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (customer_id <> executor_id)
);
-- на заказ — не более одной «живой» сделки (отменённую можно пересоздать с другим исполнителем)
CREATE UNIQUE INDEX ux_deals_order_active
    ON deals (order_id) WHERE status NOT IN ('CANCELLED', 'REFUNDED');
CREATE INDEX ix_deals_customer ON deals (customer_id, status);
CREATE INDEX ix_deals_executor ON deals (executor_id, status);

CREATE TABLE nda_agreements (                  -- NDA по сделке: снимок текста + подписи сторон
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id            UUID        NOT NULL UNIQUE REFERENCES deals (id) ON DELETE CASCADE,
    template_version   VARCHAR(16) NOT NULL DEFAULT 'v1',
    rendered_body      TEXT        NOT NULL,
    customer_signed_at TIMESTAMPTZ,
    executor_signed_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE deal_submissions (                -- сдача результата (может быть несколько итераций)
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id          UUID        NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    comment          TEXT,
    status           VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED'
                     CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REVISION_REQUESTED')),
    revision_comment TEXT,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ
);
CREATE INDEX ix_submissions_deal ON deal_submissions (deal_id, submitted_at DESC);

-- Файлы сданного результата привязываются напрямую (вместо отдельной M:N-таблицы).
ALTER TABLE files
    ADD COLUMN submission_id UUID REFERENCES deal_submissions (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_files_single_owner_entity
        CHECK (portfolio_item_id IS NULL OR submission_id IS NULL);
CREATE INDEX ix_files_submission ON files (submission_id) WHERE submission_id IS NOT NULL;

CREATE TABLE chat_threads (                    -- диалог «заказчик ↔ исполнитель» по заказу
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    customer_id     UUID        NOT NULL REFERENCES customer_profiles (user_id),
    executor_id     UUID        NOT NULL REFERENCES executor_profiles (user_id),
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, executor_id)
);
CREATE INDEX ix_threads_customer ON chat_threads (customer_id, last_message_at DESC);
CREATE INDEX ix_threads_executor ON chat_threads (executor_id, last_message_at DESC);

CREATE TABLE chat_messages (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id  UUID        NOT NULL REFERENCES chat_threads (id) ON DELETE CASCADE,
    sender_id  UUID        NOT NULL REFERENCES users (id),
    body       TEXT,
    file_id    UUID        REFERENCES files (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ,
    CHECK (body IS NOT NULL OR file_id IS NOT NULL)
);
CREATE INDEX ix_messages_thread ON chat_messages (thread_id, created_at);

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(48)  NOT NULL,          -- DEAL_FUNDED, PROPOSAL_ACCEPTED, ...
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    payload    JSONB,                          -- {"dealId":"..."} для перехода по ссылке
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, read_at, created_at DESC);

COMMENT ON TABLE proposals        IS 'Отклики исполнителей на заказы';
COMMENT ON TABLE deals            IS 'Сделка: заказ + исполнитель + сумма + зафиксированная комиссия';
COMMENT ON TABLE nda_agreements   IS 'NDA по сделке (снимок текста, подписи сторон)';
COMMENT ON TABLE deal_submissions IS 'Сдача результата по сделке (итерации приёмки)';
COMMENT ON TABLE chat_threads     IS 'Диалоги заказчик ↔ исполнитель по заказу';
COMMENT ON TABLE chat_messages    IS 'Сообщения чата';
COMMENT ON TABLE notifications    IS 'Уведомления пользователей';
