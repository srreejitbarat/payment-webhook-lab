# A focused walkthrough

Allow about 45-60 minutes for a first review. The objective is to understand and modify the system, not memorise an AI-generated answer.

1. Read `Models`, `Rules`, and `SignatureVerifier`. Draw the two allowed state transitions from CREATED. Explain why a different terminal outcome is a conflict.
2. Run `mvn verify`. Identify which tests use H2 and which PostgreSQL tests are skipped without the explicit environment variables.
3. Start the application and run `scripts/demo.py`. Match each printed outcome to an API response and a test.
4. Read `PaymentService.accept`. Identify where the transaction begins, the payment lock, the global event-ID constraint, the outbox insert and the rollback boundary.
5. Read `OutboxDispatcher`. Walk through failure on attempts one, two and the last permitted attempt. Explain why a dead event needs an operator workflow rather than an endless retry loop.
6. Make one small change yourself: for example, add a test for a missing signature header over HTTP. Predict the response before running it. Commit only the actual change and result.

## Questions drawn from the target backend roles

- How does a payment row lock differ from an in-process Java lock when multiple replicas run?
- Why is the duplicate-key handler outside the transaction callback?
- How would you demonstrate that an outbox failure rolls back a payment update?
- What happens if a message is published, then the database commit fails?
- Why is Kafka producer idempotence insufficient for that crash window?
- Why use payment ID as a Kafka partition key? Does this worker itself guarantee event ordering?
- What happens when the same event ID arrives with different payment details?
- How do clock skew and timestamp expiry interact with legitimate provider retries?
- How would secret rotation work? What must not appear in logs?
- What SLIs would you monitor: API error/latency, oldest pending age, failed attempt count and dead-event count?
- How would you extend the state machine to authorisation, capture and refund without allowing invalid transitions?
- What would you change before operating this with real payments?

## A truthful project explanation after you review it

Use your own words and say it is a new personal portfolio exercise created in October 2026 with AI assistance. Explain what you personally reviewed, ran or changed. Separate this exercise from your existing Mastercard integration work at TCS; the repository contains no employer code or private integration data.
