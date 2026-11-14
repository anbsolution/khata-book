# ANB Khata Book — Screen Specifications v1.0

The following screens define the v1.0 UI behavior.

## 00. Local Sign-in and Device Lock
- On a device with no local account, accept the business owner's existing email and a newly chosen
  local password; cloud credentials are not migrated.
- If legacy ledger rows are present, claim them for the new local account without deleting or
  changing the IDs of businesses, customers, or transactions.
- On later launches, sign in with the locally stored password hash.
- Keep the device PIN and supported biometric unlock independent from account sign-in.
- Account and ledger operations remain local to the device.

## 01. Splash
- ANB Khata Book logo and product name.
- Version label.
- Lightweight loading state.
- Route to first-run business setup or dashboard.
- Do not display fake progress or block startup unnecessarily.

## 02. Business Setup
Fields:
- Business name (required).
- Owner name (optional).
- Mobile number (optional).
- Address (optional).
- Business category (optional).
- Currency fixed to INR for v1.0.
Actions: Save and Continue.
Validation: trim whitespace; require a non-empty business name.

## 03. Dashboard
Sections:
- Business name and app header.
- Current net outstanding and customer advances, calculated from local ledger entries.
- Customer and transaction counts.
- Today's collection and today's credit.
- Recent transactions with customer, type, date and amount.
- Quick actions: Add Customer, Add Transaction, Reports, Backup.
All figures must be calculated from local records, not hard-coded sample data.

## 04. Customer List
- Search by customer name or phone.
- Add customer action.
- Customer name and optional phone.
- Current balance and status: Outstanding, Advance or Settled.
- Filters for all, outstanding, advance and settled.
- Edit and archive actions.
- Archived customers remain linked to their historical transactions.

## 05. Customer Ledger
- Customer name and phone.
- Total Udhar, total Jama and current balance.
- Transaction timeline with date, type, amount and note.
- Transaction history filter: Today, Last 7 days, This month or All time.
- Ledger totals and statement exports remain based on the full customer history.
- Actions: Add Udhar, Add Jama, WhatsApp Share, PDF statement.
- PDF export saves a paginated statement with customer totals and the full transaction history.
- WhatsApp action opens an editable preview before handing off to WhatsApp.
- Do not expose unrelated customers' data in the share preview.

## 06. Add Transaction
Fields:
- Customer (required).
- Type: Udhar or Jama (required).
- Amount in INR (required, greater than zero).
- Transaction date (required; default to current device date).
- Payment method (optional; cash, UPI, bank, other).
- Description/note (optional).
Actions: Save and Cancel.
Show a clear summary before saving when appropriate.

## 07. WhatsApp Share Preview
- Customer name and phone number; invalid numbers show a warning and use the system share sheet.
- Business name.
- Total Udhar, total Jama and current balance.
- Up to five recent transaction entries.
- Editable message text.
- Button to open WhatsApp or WhatsApp Business with a prefilled message.
- User must review and manually press Send inside WhatsApp.
- If WhatsApp is unavailable, open the Android Sharesheet with the reviewed message.
- Sending requires internet and WhatsApp availability; local ledger remains offline.
- Never claim delivery/read status unless a supported source provides it.

## 08. Reports
- Today, last-seven-days, current-month and all-time summaries.
- Udhar, Jama, period net, and transaction count from local ledger entries.
- Current outstanding balances by customer.
- Recent period transactions.
- Customer statement.
- Outstanding report.
- Collection report.
- Date range selection.
- PDF and CSV export for the selected period, including all period transactions and current outstanding balances.
- Report totals must reconcile with the ledger calculation engine.

## 09. Backup and Restore
- Export full local data to JSON.
- Optional encrypted backup.
- Import/restore from a selected backup file.
- Validate schema/version and file integrity before restore.
- Explain overwrite/merge behavior before the user confirms.
- Provide a clear completion or failure result.
- Backup and restore must work without network access.

## 10. Settings
- Editable business name stored in the local business profile.
- App lock/PIN.
- Biometric unlock where device/platform supports it.
- Language: Hindi and English.
- Theme preferences.
- Backup and restore.
- Statement-sharing preference: automatic, WhatsApp, WhatsApp Business or Android share sheet.
- Data management.
- About and version information.

## Global states
Every data-driven screen must define:
- Empty state.
- Loading state.
- Error state with recovery action.
- Offline state where relevant.
- Success confirmation after a completed local operation.
