# Security Policy

## Supported Versions

Only the latest release receives security fixes.

| Version | Supported |
|---------|-----------|
| 1.2.x   | ✅        |
| < 1.2   | ❌        |

## Threat model

- The HTTP server listens only on `127.0.0.1`.
- `Host` and any supplied `Origin` are checked on every `/mcp` and `/health` request, even with authentication off.
- Only loopback hosts and origins on the server's port are allowed; no CORS headers are sent, so an unrelated browser page cannot control the server.
- Token authentication is off by default: any program running under the same account or on the same machine can control Sweet Home 3D.
- This includes writing files with `save_home`, `export_*` and `render_photo` wherever Sweet Home 3D has write access.
- Enable **Require a token** in **Tools → MCP Server...** if untrusted programs or other users share the machine; a token does not isolate programs that can read your account's files.
- `load_home` treats files as untrusted: it reads validated `Home.xml` without Java deserialization, XML external entities (XXE), or external model, texture or image content.
- Loading requires a regular file with exactly one `Home.xml`, no DOCTYPE, and at most 64 MB of uncompressed XML; older files must first be opened and saved in Sweet Home 3D.

## Reporting a Vulnerability

**Please do not report security vulnerabilities as public GitHub Issues.**

GitHub Private Vulnerability Reporting is enabled. Open the repository's **Security → Report a vulnerability** form ([private report](../../security/advisories/new)). This lets us assess and fix the issue before public disclosure.

Include in your report:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Sweet Home 3D and plugin version

We aim to respond within 7 days and will credit you in the release notes if you wish.
