-- =====================================================================
-- V4 — ДОВЕРИЕ (веха «Верификация и рейтинги», 27.12.2026)
-- Верификация исполнителя и отзывы.
-- До V4 исполнителей можно переводить в VERIFIED вручную (UPDATE), чтобы
-- проверять сценарий «отклик → сделка», который требует верификации.
-- =====================================================================

CREATE TABLE verification_requests (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    executor_id      UUID        NOT NULL REFERENCES executor_profiles (user_id) ON DELETE CASCADE,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    inn              VARCHAR(12) NOT NULL CHECK (inn ~ '^[0-9]{12}$'),   -- ИНН физлица (тестовый)
    passport_file_id UUID        NOT NULL REFERENCES files (id),         -- только скан, номер не хранится
    inn_file_id      UUID        REFERENCES files (id),
    auto_checks      JSONB,                                              -- {"innChecksum":true,"portfolioCount":3}
    reject_reason    TEXT,
    reviewer_id      UUID        REFERENCES users (id),
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ,
    CHECK (reviewed_at IS NULL OR reviewed_at >= submitted_at),
    CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);
CREATE UNIQUE INDEX ux_verification_one_pending
    ON verification_requests (executor_id) WHERE status = 'PENDING';
CREATE INDEX ix_verification_queue ON verification_requests (status, submitted_at);

CREATE TABLE reviews (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id    UUID        NOT NULL REFERENCES deals (id),
    author_id  UUID        NOT NULL REFERENCES users (id),
    target_id  UUID        NOT NULL REFERENCES users (id),
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (deal_id, author_id),                 -- один отзыв от стороны на сделку
    CHECK (author_id <> target_id)
);
CREATE INDEX ix_reviews_target ON reviews (target_id, created_at DESC);

COMMENT ON TABLE verification_requests IS 'Заявки на верификацию (паспорт, ИНН, портфолио)';
COMMENT ON TABLE reviews               IS 'Отзывы и оценки сторон по завершённой сделке';
