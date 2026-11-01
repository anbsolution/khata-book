# ANB Khata Book — Screen Specifications v1.0

The following screens define the v1.0 UI scope. These are specifications, not implemented screens.

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
- Total outstanding.
- Total credit given (Udhar).
- Total payments received (Jama).
- Today's collection and today's credit.
- Customer count.
- Recent transactions.
- Quick actions: Add Customer, Add Transaction, Reports, Backup.
All figures must be calculated from local records, not hard-coded sample data.

## 04. Customer List
- Search by customer name or phone.
- Add customer action.
- Customer name and optional phone.
- Current balance and status: Outstanding, Advance, Settled.
- Filters for all, outstanding, advance and settled.
- Edit and archive actions.
- Archived customers remain linked to their historical transactions.

## 05. Customer Ledger
- Customer name and phone.
- Total Udhar, total Jama and current balance.
- Transaction timeline with date, type, amount and note.
- Date filter.
- Actions: Add Udhar, Add Jama, WhatsApp Share, PDF statement.
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
- Customer name and validated phone number.
- Business name.
- Total Udhar, total Jama and current balance.
- Optional recent transaction summary.
- Editable message text.
- Button to open WhatsApp with a prefilled message.
- User must review and manually press Send inside WhatsApp.
- If WhatsApp is unavailable, offer the system share sheet or copy-message fallback.
- Sending requires internet and WhatsApp availability; local ledger remains offline.
- Never claim delivery/read status unless a supported source provides it.

## 08. Reports
- Daily, weekly and monthly summaries.
- Customer statement.
- Outstanding report.
- Collection report.
- Date range selection.
- PDF and CSV export.
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
- Business profile.
- App lock/PIN.
- Biometric unlock where device/platform supports it.
- Language: Hindi and English.
- Theme preferences.
- Backup and restore.
- WhatsApp sharing preferences.
- Data management.
- About and version information.

## Global states
Every data-driven screen must define:
- Empty state.
- Loading state.
- Error state with recovery action.
- Offline state where relevant.
- Success confirmation after a completed local operation.
