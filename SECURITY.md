# Security reporting

For a suspected vulnerability, use the repository's **Security → Report a vulnerability** form:

https://github.com/n5za/ir-hunter/security/advisories/new

Include affected version, reproduction steps, expected/actual behavior and impact. Remove personal data, signing credentials and unrelated imported remote files. Use ordinary Issues for compatibility problems and non-sensitive bugs.

The current published artifact is a debug test build. No production release support or response-time commitment is established. The app has no internet permission and stores imported profiles locally; malicious or malformed `.ir` parsing, excessive allocations and unintended transmissions are relevant security areas.
