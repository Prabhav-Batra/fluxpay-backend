alter table payment_intents add column if not exists gateway_payment_id varchar(255);
create index if not exists idx_payment_intents_gateway_ref on payment_intents (gateway_provider, gateway_reference);
