# ANB Khata Book

Native Android ledger app for a single business. Customer and transaction data remains in the
app-private Room/SQLite database. The app routes ledger operations through a dedicated local HTTP
server bound only to `127.0.0.1`; there is no remote backend or cloud dependency.

The first local setup uses the business owner's existing email and a newly chosen local password.
The existing device PIN/biometric lock remains available. Encrypted backup and restore are explicit
local operations; no automatic synchronization occurs.

See [the Android architecture](docs/native-architecture-v2.md) and the UI/product specifications
under `docs/`.
