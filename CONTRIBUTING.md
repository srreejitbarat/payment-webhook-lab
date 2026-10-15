# Contributing

Use JDK 17+. Run `mvn verify` and `node --test demo/lab.test.mjs` before submitting a change. Enable real PostgreSQL tests with the documented TEST_POSTGRES environment variables.

Keep changes focused. Explain the trigger and expected behavior and add meaningful failure-case tests. Distinguish browser simulation, mocked broker contracts and real infrastructure verification. Keep credentials, database files and build outputs out of commits.
