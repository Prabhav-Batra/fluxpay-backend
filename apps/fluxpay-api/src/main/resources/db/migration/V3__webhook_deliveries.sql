-- Outbox for outgoing merchant webhooks: one row per (event, endpoint).
-- Replaces the Redis queue + separate fluxpay-webhook-worker service.
create table webhook_deliveries (
    id uuid not null primary key,
    event_id uuid not null,
    merchant_id uuid not null,
    endpoint_id uuid,
    event_type varchar(100) not null,
    url varchar(2048) not null,
    payload text not null,
    status varchar(20) not null check (status in ('PENDING','DELIVERED','FAILED')),
    attempts integer not null,
    next_attempt_at timestamp(6) with time zone,
    last_response_status integer,
    last_error varchar(500),
    delivered_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone
);
create index idx_webhook_deliveries_status on webhook_deliveries (status, next_attempt_at);
create index idx_webhook_deliveries_merchant on webhook_deliveries (merchant_id, created_at);
