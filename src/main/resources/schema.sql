create table if not exists payments (
  payment_id varchar(36) primary key,
  amount_minor bigint not null check (amount_minor > 0),
  currency varchar(3) not null,
  status varchar(16) not null check (status in ('CREATED','SUCCEEDED','FAILED'))
);
create table if not exists event_receipts (
  event_id varchar(80) primary key,
  payment_id varchar(36) not null references payments(payment_id),
  amount_minor bigint not null,
  currency varchar(3) not null,
  status varchar(16) not null check (status in ('SUCCEEDED','FAILED'))
);
create table if not exists outbox (
  event_id varchar(80) primary key references event_receipts(event_id),
  payment_id varchar(36) not null references payments(payment_id),
  payload varchar(2048) not null,
  state varchar(16) not null check (state in ('PENDING','DELIVERED','DEAD')),
  attempts integer not null check (attempts >= 0),
  next_attempt_ms bigint not null
);
create index if not exists outbox_due on outbox(state,next_attempt_ms);
