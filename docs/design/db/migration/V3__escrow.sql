-- =====================================================================
-- V3 — ЭСКРОУ (веха «Эскроу-платежи», 13.12.2026)
-- Платежи ЮKassa/Тинькофф (песочница), журнал webhook-ов, споры.
-- Обсуждение спора ведётся в обычном чате заказа (chat_threads).
-- =====================================================================

CREATE TABLE payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id             UUID          NOT NULL REFERENCES deals (id),
    type                VARCHAR(16)   NOT NULL
                        CHECK (type IN ('DEPOSIT', 'PAYOUT', 'REFUND')),
    provider            VARCHAR(16)   NOT NULL
                        CHECK (provider IN ('YOOKASSA', 'TINKOFF')),
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'AUTHORIZED', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)       NOT NULL DEFAULT 'RUB',
    provider_payment_id VARCHAR(128),
    idempotency_key     VARCHAR(64)   NOT NULL UNIQUE,   -- защита от двойного списания/выплаты
    confirmation_url    TEXT,                            -- куда отправить заказчика для оплаты
    failure_reason      VARCHAR(255),
    raw_response        JSONB,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_payments_provider_id
    ON payments (provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX ix_payments_deal ON payments (deal_id, type);

CREATE TABLE payment_webhook_events (          -- журнал входящих уведомлений (идемпотентная обработка)
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider         VARCHAR(16)  NOT NULL CHECK (provider IN ('YOOKASSA', 'TINKOFF')),
    event_id         VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    payload          JSONB        NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    processing_error TEXT,
    UNIQUE (provider, event_id)
);

CREATE TABLE disputes (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id            UUID          NOT NULL REFERENCES deals (id),
    opened_by          UUID          NOT NULL REFERENCES users (id),
    reason             TEXT          NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED')),
    resolution         VARCHAR(24)
                       CHECK (resolution IN ('RELEASE_TO_EXECUTOR', 'REFUND_TO_CUSTOMER', 'SPLIT')),
    executor_amount    NUMERIC(14,2) CHECK (executor_amount IS NULL OR executor_amount >= 0),
    customer_amount    NUMERIC(14,2) CHECK (customer_amount IS NULL OR customer_amount >= 0),
    resolution_comment TEXT,
    resolved_by        UUID          REFERENCES users (id),
    opened_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sla_due_at         TIMESTAMPTZ   NOT NULL,   -- opened_at + 3 рабочих дня (критерий приёмки №2)
    resolved_at        TIMESTAMPTZ,
    CHECK (status <> 'RESOLVED'
           OR (resolution IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_disputes_one_active ON disputes (deal_id) WHERE status <> 'RESOLVED';
CREATE INDEX ix_disputes_queue ON disputes (status, sla_due_at);

COMMENT ON TABLE payments               IS 'Платежи эскроу: депозит, выплата, возврат';
COMMENT ON TABLE payment_webhook_events IS 'Журнал webhook-событий ЮKassa/Тинькофф';
COMMENT ON TABLE disputes               IS 'Споры по сделкам (SLA ≤ 3 рабочих дня)';
