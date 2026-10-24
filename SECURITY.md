# Security

## Reporting a vulnerability

Do not open a public issue with an API key, token, or account data. Report security issues privately to the repository owner, or use GitHub's private vulnerability reporting if it is enabled.

## Key handling

- The API key is stored in `EncryptedSharedPreferences` using an AES256-GCM master key from the Android Keystore.
- The key is never written to logs, widget previews, or crash reports.
- Android backup and device transfer are disabled for the app.
- The app only sends the key to `https://opencode.ai/console/api/go/status`.
- Cleartext traffic and HTTP redirects are disabled.

## API key permissions

The app itself only performs a read-only GET. The console currently rejects keys with only `usage:read` or read-only permissions; use the Go API key or a key with `all`. See [docs/API_KEY.md](docs/API_KEY.md).

## Release signing

Do not publish a debug-signed APK. The GitHub release workflow requires these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow fails if they are missing.

## Scope

This is an unofficial client. Vulnerabilities in OpenCode's own service should be reported to OpenCode, not to this project.

## Supported versions

Only the latest release is supported.
