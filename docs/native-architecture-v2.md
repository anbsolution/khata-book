# Khata Book — Local-only Android Architecture

## Platform and data ownership
- Native Android application using Kotlin, Jetpack Compose, and Material 3.
- Room/SQLite in the app-private directory is the durable source of truth.
- Business, customer, and transaction reads and writes go through the app's local HTTP API.
- The API server runs in the Android app process and binds only to `127.0.0.1` on an ephemeral port.
- No remote backend, cloud database, telemetry, or third-party authentication service is used.

## Local API security
- Every request carries a cryptographically random, app-private token.
- The token is generated once and retained in app-private preferences; the current port is local-only.
- HTTP cleartext is allowed only for `127.0.0.1`; other cleartext hosts are denied.
- The server accepts loopback connections only, limits request headers and bodies, checks ownership,
  and returns explicit error status codes without exposing internal exceptions.
- The listener exposes versioned account, business, customer, transaction, and local backup routes.

## Local account and device lock
- First-run setup accepts the user's existing email and a newly chosen local password. Existing
  cloud passwords are not migrated; passwords use salted PBKDF2-HMAC-SHA256 hashes, with an
  algorithm-tagged PBKDF2-HMAC-SHA1 fallback only on Android providers lacking SHA-256 support.
- On the first local account setup, legacy on-device businesses are claimed by that account without
  changing business, customer, or transaction IDs or deleting ledger data.
- The existing device PIN and biometric unlock remain device-local and independent of the password.
- When a PIN is configured, the app returns to the lock screen after it has stayed in the background
  for one minute.
- Appearance can follow the system setting or be fixed to light or dark mode.

## Backup and restore
- Encrypted backup and restore remain on-device through the local API and Android document picker.
- Daily and weekly automatic backups are encrypted with an Android Keystore key and retained in
  app-private storage (up to seven copies of each cadence). They are removed when the app is
  uninstalled; use a password-protected manual backup to transfer data or keep a portable copy.
- Backup restore validates references and replaces the local ledger transactionally; restored
  businesses are assigned to the currently signed-in local account.
- The database migration removes only the obsolete synchronization queue. It preserves all ledger
  tables and their existing rows.

## Database migration
- Schema version 3 adds `local_accounts` and removes the unused `sync_queue`.
- Migrations support existing database versions 1 and 2, preserving business, customer, and
  transaction records.
- Existing encrypted backups remain readable; newly created backups omit cloud synchronization
  metadata.
