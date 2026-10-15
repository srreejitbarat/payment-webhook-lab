# Verification record

Date: 15 October 2026. Java 17.0.20, Maven 3.9.11, Spring Boot 4.1.1.

## Executed in the cloud workspace

- `mvn verify`: BUILD SUCCESS. 30 discovered tests: 27 passed, zero failures or errors, 3 explicitly skipped PostgreSQL tests.
- Covered: signature tampering/expiry, invalid money, conflicting state, receipt/outbox atomic rollback, 16 concurrent duplicates, a cross-payment event-ID race, unknown JSON rejection, retry scheduling/dead events, real HTTP requests, body limit and mocked Kafka acknowledgement/failure.
- Packaged JAR started successfully and passed the Python HTTP demo: create payment, reject forged signature, apply, deduplicate, ignore repeated outcome, reject a contradictory terminal outcome.
- Scheduled outbox delivery to the default log sink observed as DELIVERED.
- Application stopped and restarted; file-backed H2 preserved the SUCCEEDED payment.
- `python3 -m py_compile scripts/demo.py scripts/setup_env.py`: passed.
- Interactive browser simulator: 5 Node tests passed, covering duplicates, signature tampering/expiry, terminal conflict, retry limits and single acknowledgement.

## GitHub Actions

- [Run #1](https://github.com/srreejitbarat/payment-webhook-lab/actions/runs/38725635834) succeeded on commit `64117caf27d14f837f52178cb10f2ab880162484` on 15 October 2026.
- CI Java result: **30 tests passed, zero failures/errors/skips**, including all three PostgreSQL integration tests against the workflow PostgreSQL 17 service.
- Python syntax and the browser simulation behavior steps also succeeded.
- Non-blocking runner annotations flag older setup actions for migration.

## Not yet verified

- Real Kafka broker delivery: not run. Kafka adapter tests use mocks.
- Docker image and Compose execution: not run in this workspace.
- The hosted interactive page simulates application state in the browser. It is not a hosted Java API.

The first test run exposed an unavailable JVM attachment mechanism for Mockito. The project now explicitly uses Mockito subclass mocking, suitable for its non-final mocked KafkaTemplate. The full Maven verification was rerun successfully after that change.
