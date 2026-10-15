# Security scope

This is a local learning project using synthetic data. It has not undergone a production security assessment.

Webhooks require a timestamped HMAC over raw bytes. Admin endpoints require a separate API key. Startup rejects short keys; the setup script generates fresh local values. Keep local demonstrations bound to loopback.

Do not include real credentials, customer data or payment payloads in public issues. Use GitHub private vulnerability reporting if available for this repository, or describe only non-sensitive reproduction details in an issue.
