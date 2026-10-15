# Architecture and decisions

## Request and transaction boundaries

The controller reads a bounded raw body. HMAC and timestamp validation occur before JSON parsing. Rules validate identifiers, money and terminal state transitions.

PaymentService locks the payment row, inspects any prior receipt, validates the transition, records the event, updates the payment and writes an outbox record in one transaction. The same ID and business content is DUPLICATE. A new ID repeating the current terminal result is IGNORED without a second outbox event.

Two different payments can race on the same global event ID without sharing a payment lock. The database primary key is the final arbiter. A uniqueness exception is handled after rollback, avoiding reads inside an aborted PostgreSQL transaction.

## Delivery model

Workers claim a due PENDING row with FOR UPDATE SKIP LOCKED. Acknowledgement produces DELIVERED; failures advance the attempt counter and schedule capped exponential backoff. The final allowed failure produces DEAD.

The external publish occurs while the database row remains locked. This simplifies claim coordination but reduces throughput. A crash between publication and commit can cause redelivery. Consumers must deduplicate by event ID; Kafka producer idempotence cannot remove that application crash window.

## Storage and limits

H2 file storage is a persistent single-instance demo. PostgreSQL is the intended shared database for the Docker setup and explicit integration tests. The bootstrap schema is not a versioned migration history.

The model has only two terminal outcomes. Refunds, authorisation/capture, reconciliation, retention policy, lease-based workers, dead-letter replay and production observability are outside the implemented scope. No throughput or production-scale claim is made.
