# Проектирование: ERD БД и REST API

**Проект:** B2B-маркетплейс ИТ-услуг с верификацией и эскроу
**Веха:** «Завершение сбора требований и проектирования (ERD, API)» — 01.11.2026
**Стек по паспорту проекта:** Java/Spring + React + PostgreSQL + ЮKassa/Тинькофф (песочница)

## Состав пакета

| Файл | Что внутри |
|---|---|
| `erd.png` / `erd.svg` | ERD: 22 таблицы, 38 внешних ключей; цвет = миграция. Построена из реальной БД |
| `erd.md` | Этот документ + Mermaid-версия ERD |
| `db/migration/V1…V4` | Flyway-миграции PostgreSQL 14+ (применены на PostgreSQL 16) |
| `nda_template.txt` | Текст NDA-шаблона (в ресурсы приложения, не в БД) |
| `openapi.yaml` | OpenAPI 3.0.3: 66 путей, 76 операций, 69 схем (валидирован) |

## Миграции по вехам

| Файл | Веха | Содержание | Таблиц | Эндпоинтов |
|---|---|---|---|---|
| `V1__core.sql` | до 15.11 | Ядро: регистрация, профили, портфолио, заказы | 10 | 33 |
| `V2__deals.sql` | до 29.11 | Сделки: отклики, сделка, NDA, сдача результата, чат, уведомления | 7 | 24 |
| `V3__escrow.sql` | до 13.12 | Эскроу: платежи, webhook-и, споры | 3 | 10 |
| `V4__trust.sql` | до 27.12 | Доверие: верификация, отзывы | 2 | 9 |
| `V1_1__seed_reference_data.sql` | до 15.11 | 28 навыков, ставка комиссии 12 % | — | — |

Порядок применения Flyway: `V1` → `V1.1` → `V2` → `V3` → `V4`. Каждая следующая миграция только **добавляет** таблицы (кроме одной команды `ALTER TABLE files` в V2), поэтому можно спокойно работать на V1 и подключать остальное по мере готовности. Пока нет V4 (верификации), правило «откликаться могут только `VERIFIED`» удобно отключить флагом конфигурации или вручную проставить исполнителям `verification_status = 'VERIFIED'`.

## ERD

![ERD](erd.png)

**V1__core.sql** — Ядро: регистрация, профили, портфолио, заказы

| Таблица | Назначение |
|---|---|
| `users` | Учётные записи (заказчик, исполнитель, админ) |
| `customer_profiles` | Профиль заказчика (CTO/PM) и данные его компании |
| `executor_profiles` | Профиль исполнителя, статус верификации, рейтинг |
| `skills` | Справочник навыков/технологий |
| `executor_skills` | Навыки исполнителя (M:N) |
| `portfolio_items` | Работы в портфолио исполнителя |
| `files` | Метаданные загруженных файлов |
| `orders` | Заказы заказчиков |
| `order_skills` | Требуемые навыки заказа (M:N) |
| `platform_settings` | Настройки платформы (ставка комиссии 10–15 %) |

**V2__deals.sql** — Сделки: отклики, сделка, NDA, сдача результата, чат, уведомления

| Таблица | Назначение |
|---|---|
| `proposals` | Отклики исполнителей на заказы |
| `deals` | Сделка: заказ + исполнитель + сумма + зафиксированная комиссия |
| `nda_agreements` | NDA по сделке (снимок текста, подписи сторон) |
| `deal_submissions` | Сдача результата по сделке (итерации приёмки) |
| `chat_threads` | Диалоги заказчик ↔ исполнитель по заказу |
| `chat_messages` | Сообщения чата |
| `notifications` | Уведомления пользователей |

**V3__escrow.sql** — Эскроу: платежи, webhook-и, споры

| Таблица | Назначение |
|---|---|
| `payments` | Платежи эскроу: депозит, выплата, возврат |
| `payment_webhook_events` | Журнал webhook-событий ЮKassa/Тинькофф |
| `disputes` | Споры по сделкам (SLA ≤ 3 рабочих дня) |

**V4__trust.sql** — Доверие: верификация, отзывы

| Таблица | Назначение |
|---|---|
| `verification_requests` | Заявки на верификацию (паспорт, ИНН, портфолио) |
| `reviews` | Отзывы и оценки сторон по завершённой сделке |

## Ключевые решения

1. **Сделка — центральная сущность.** Сумма, ставка и выплата фиксируются при создании; `commission_amount` и `executor_payout` вычисляются самой БД (`GENERATED … STORED`), поэтому разойтись не могут. Смена ставки в `platform_settings` на существующие сделки не влияет.
2. **Эскроу = платежи + статусы сделки.** `payments` (`DEPOSIT`, `PAYOUT`, `REFUND`) хранит операции у провайдера, `idempotency_key` защищает от двойных списаний, `payment_webhook_events` с уникальностью `(provider, event_id)` — от повторной обработки уведомлений.
3. **Правила защищены в БД:** комиссия 10–15 % (`CHECK`), одна живая сделка на заказ, один открытый спор на сделку, одна заявка на верификацию в `PENDING`, один отклик на заказ, один отзыв стороны на сделку.
4. **Без реальных ПДн:** номер паспорта не хранится, только тестовый скан в `files`.
5. **Статусы — `VARCHAR + CHECK`, идентификаторы — UUID.**

Для Spring/JPA: для `deals.commission_amount` и `deals.executor_payout` используйте `@Column(insertable = false, updatable = false)`; `rating_avg`, `rating_count`, `completed_deals` обновляйте в той же транзакции, что и отзыв/завершение сделки; `updated_at` — через `@LastModifiedDate` (триггеров нет).

## Трассировка: критерии приёмки → БД и API

| Критерий | Таблицы | Эндпоинты | Миграция |
|---|---|---|---|
| **1.** Верификация (паспорт, ИНН, портфолио) ≤ 5 мин | `verification_requests`, `executor_profiles.verification_status`, `portfolio_items` | `POST /verification-requests`, `/admin/verification-requests/**`, `avgProcessingSeconds` в `GET /admin/stats` | V4 |
| **2.** Эскроу до приёмки; спор ≤ 3 раб. дней | `deals`, `payments`, `payment_webhook_events`, `deal_submissions`, `disputes.sla_due_at` | `POST /deals/{id}/fund`, `/submissions`, `/accept`, `/request-revision`, `/disputes`, `POST /webhooks/*`, `POST /admin/disputes/{id}/resolve` | V2 + V3 |
| **3.** Комиссия настраивается 10–15 % | `platform_settings`, `deals.commission_*` | `GET /commission/current`, `PUT /admin/commission` | V1 + V2 |
| **4.** Пилот: ≥ 20 сделок, 50+ исполнителей, 10+ заказчиков | `deals`, профили | `GET /admin/stats` | V4 |
| NDA-шаблоны | `nda_agreements` + `nda_template.txt` | `/deals/{id}/nda`, `/deals/{id}/nda/sign` | V2 |
| Рейтинги | `reviews`, `executor_profiles.rating_*` | `/deals/{id}/reviews`, `/users/{id}/reviews` | V4 |

Критерий 5 (GTM, юнит-экономика, выход на Allverse) — бизнес-часть, в БД/API не отражается.

## Вопросы к заказчику и куратору

1. **Кто платит комиссию.** Допущение: удерживается из выплаты исполнителю. Если сверху с заказчика — меняется формула в `deals`.
2. **Подписка для заказчиков** есть в бизнес-модели паспорта, но не в перечне «Входит» устава — в MVP не включена.
3. **Эскроу в песочнице.** Как именно удерживать деньги и выплачивать исполнителю (холд, счёт платформы, выплаты), нужно проверить по актуальной документации ЮKassa и Тинькофф: у холда есть ограничения по сроку, а выплаты могут быть недоступны в тестовом режиме. Рекомендую интерфейс `PaymentProvider` с возможностью подменить реализацию заглушкой.
4. **Налоговый статус исполнителя** (самозанятый, ИП) и чеки не моделируются.
5. **Чат в реальном времени.** В MVP — polling; WebSocket/SSE добавляются без изменений БД.

## Mermaid-версия ERD

Для вставки в Markdown-документацию (GitHub, GitLab, Notion, Obsidian) или в [mermaid.live](https://mermaid.live):

```mermaid
erDiagram
    users {
        uuid id PK
        varchar email
        varchar password_hash
        varchar role
        varchar status
        varchar full_name
        varchar phone
        timestamptz email_confirmed_at
        timestamptz last_login_at
        timestamptz created_at
        timestamptz updated_at
    }
    customer_profiles {
        uuid user_id PK,FK
        varchar job_title
        varchar company_name
        varchar company_inn
        varchar company_website
        text company_description
    }
    executor_profiles {
        uuid user_id PK,FK
        varchar display_name
        varchar headline
        text bio
        numeric hourly_rate
        smallint experience_years
        bool is_student
        varchar university
        varchar availability
        varchar verification_status
        timestamptz verified_at
        numeric rating_avg
        int rating_count
        int completed_deals
        timestamptz created_at
        timestamptz updated_at
    }
    skills {
        smallint id PK
        varchar name
        varchar category
    }
    executor_skills {
        uuid executor_id PK,FK
        smallint skill_id PK,FK
        varchar level
        smallint years_experience
    }
    portfolio_items {
        uuid id PK
        uuid executor_id FK
        varchar title
        text description
        varchar project_url
        smallint completed_year
        timestamptz created_at
        timestamptz updated_at
    }
    files {
        uuid id PK
        uuid owner_id FK
        uuid portfolio_item_id FK
        varchar storage_key
        varchar original_name
        varchar content_type
        bigint size_bytes
        char sha256
        varchar purpose
        timestamptz created_at
        uuid submission_id FK
    }
    orders {
        uuid id PK
        uuid customer_id FK
        varchar title
        text description
        varchar category
        numeric budget_min
        numeric budget_max
        date deadline_at
        bool nda_required
        varchar status
        timestamptz published_at
        timestamptz created_at
        timestamptz updated_at
    }
    order_skills {
        uuid order_id PK,FK
        smallint skill_id PK,FK
    }
    platform_settings {
        varchar key PK
        varchar value
        uuid updated_by FK
        timestamptz updated_at
    }
    proposals {
        uuid id PK
        uuid order_id FK
        uuid executor_id FK
        text cover_letter
        numeric price
        smallint duration_days
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
    deals {
        uuid id PK
        uuid order_id FK
        uuid proposal_id FK
        uuid customer_id FK
        uuid executor_id FK
        numeric amount
        char currency
        numeric commission_rate
        numeric commission_amount
        numeric executor_payout
        varchar status
        date deadline_at
        timestamptz funded_at
        timestamptz accepted_at
        timestamptz completed_at
        timestamptz cancelled_at
        timestamptz created_at
        timestamptz updated_at
    }
    nda_agreements {
        uuid id PK
        uuid deal_id FK
        varchar template_version
        text rendered_body
        timestamptz customer_signed_at
        timestamptz executor_signed_at
        timestamptz created_at
    }
    deal_submissions {
        uuid id PK
        uuid deal_id FK
        text comment
        varchar status
        text revision_comment
        timestamptz submitted_at
        timestamptz reviewed_at
    }
    chat_threads {
        uuid id PK
        uuid order_id FK
        uuid customer_id FK
        uuid executor_id FK
        timestamptz last_message_at
        timestamptz created_at
    }
    chat_messages {
        uuid id PK
        uuid thread_id FK
        uuid sender_id FK
        text body
        uuid file_id FK
        timestamptz created_at
        timestamptz read_at
    }
    notifications {
        uuid id PK
        uuid user_id FK
        varchar type
        varchar title
        text body
        jsonb payload
        timestamptz read_at
        timestamptz created_at
    }
    payments {
        uuid id PK
        uuid deal_id FK
        varchar type
        varchar provider
        varchar status
        numeric amount
        char currency
        varchar provider_payment_id
        varchar idempotency_key
        text confirmation_url
        varchar failure_reason
        jsonb raw_response
        timestamptz created_at
        timestamptz updated_at
        timestamptz completed_at
    }
    payment_webhook_events {
        bigint id PK
        varchar provider
        varchar event_id
        varchar event_type
        jsonb payload
        timestamptz received_at
        timestamptz processed_at
        text processing_error
    }
    disputes {
        uuid id PK
        uuid deal_id FK
        uuid opened_by FK
        text reason
        varchar status
        varchar resolution
        numeric executor_amount
        numeric customer_amount
        text resolution_comment
        uuid resolved_by FK
        timestamptz opened_at
        timestamptz sla_due_at
        timestamptz resolved_at
    }
    verification_requests {
        uuid id PK
        uuid executor_id FK
        varchar status
        varchar inn
        uuid passport_file_id FK
        uuid inn_file_id FK
        jsonb auto_checks
        text reject_reason
        uuid reviewer_id FK
        timestamptz submitted_at
        timestamptz reviewed_at
    }
    reviews {
        uuid id PK
        uuid deal_id FK
        uuid author_id FK
        uuid target_id FK
        smallint rating
        text comment
        timestamptz created_at
    }
    users ||--|| customer_profiles : "user_id"
    users ||--|| executor_profiles : "user_id"
    executor_profiles ||--o{ executor_skills : "executor_id"
    skills ||--o{ executor_skills : "skill_id"
    executor_profiles ||--o{ portfolio_items : "executor_id"
    users ||--o{ files : "owner_id"
    portfolio_items |o--o{ files : "portfolio_item_id"
    customer_profiles ||--o{ orders : "customer_id"
    orders ||--o{ order_skills : "order_id"
    skills ||--o{ order_skills : "skill_id"
    users |o--o{ platform_settings : "updated_by"
    orders ||--o{ deals : "order_id"
    orders ||--o{ proposals : "order_id"
    executor_profiles ||--o{ proposals : "executor_id"
    proposals ||--|| deals : "proposal_id"
    customer_profiles ||--o{ deals : "customer_id"
    executor_profiles ||--o{ deals : "executor_id"
    deals ||--|| nda_agreements : "deal_id"
    deals ||--o{ deal_submissions : "deal_id"
    deal_submissions |o--o{ files : "submission_id"
    orders ||--o{ chat_threads : "order_id"
    customer_profiles ||--o{ chat_threads : "customer_id"
    executor_profiles ||--o{ chat_threads : "executor_id"
    chat_threads ||--o{ chat_messages : "thread_id"
    users ||--o{ chat_messages : "sender_id"
    files |o--o{ chat_messages : "file_id"
    users ||--o{ notifications : "user_id"
    deals ||--o{ payments : "deal_id"
    deals ||--o{ disputes : "deal_id"
    users ||--o{ disputes : "opened_by"
    users |o--o{ disputes : "resolved_by"
    executor_profiles ||--o{ verification_requests : "executor_id"
    files ||--o{ verification_requests : "passport_file_id"
    files |o--o{ verification_requests : "inn_file_id"
    users |o--o{ verification_requests : "reviewer_id"
    deals ||--o{ reviews : "deal_id"
    users ||--o{ reviews : "author_id"
    users ||--o{ reviews : "target_id"
```
