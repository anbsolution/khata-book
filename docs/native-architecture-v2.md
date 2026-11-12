# Khata Book 2.0 — Native Android Architecture

## Platform
- Native Android application
- Kotlin, Jetpack Compose and Material 3
- Package: com.anbsolution.khatabook
- Local persistence: Room / SQLite
- Background synchronization foundation: WorkManager dependency
- Preferences: DataStore

## Data model foundation
- BusinessEntity
- CustomerEntity
- LedgerTransactionEntity
- SyncQueueEntity

## Security and ownership
Each business record is associated with an account identifier.
Cloud authorization and row-level ownership enforcement must be implemented
before production use.

## Planned implementation milestones
1. Native Android build foundation
2. Appwrite email/password authentication
3. Business profile and account ownership
4. Customer management and ledger entries
5. Offline-first repository and reliable cloud synchronization
6. Reports, PDF/CSV export, backup and restore
7. Security review and end-to-end testing

## Current limitations
This initial foundation does not yet implement authentication, cloud sync,
customer CRUD screens, PDF/CSV export, backup/restore, or production security.
Do not use it as a finished accounting application.
