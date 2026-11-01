# UX, Accessibility and Financial Interaction Rules

## Financial clarity
- Show INR symbol and Indian digit grouping consistently.
- Label totals explicitly: Udhar, Jama, Baaki/Outstanding and Advance.
- Never rely on color alone to distinguish Udhar and Jama.
- Show date and transaction type on every ledger entry.
- Avoid ambiguous signs; explain whether a balance is payable or advance.

## Transaction safety
- Save each transaction atomically.
- Prevent accidental duplicate submissions with an idempotency mechanism.
- Require confirmation for reversal and destructive actions.
- Preserve the original transaction record; corrections use a reversal/correction trail.
- Do not silently change historical transaction dates or amounts.
- Use integer paise for internal monetary calculations, not floating-point arithmetic.

## WhatsApp privacy
- Preview the exact message before opening WhatsApp.
- Include only the selected customer's details.
- Normalize and validate phone numbers before constructing the share action.
- Do not send automatically or in the background.
- User manually confirms sending in WhatsApp.
- Explain that WhatsApp sending needs connectivity; local bookkeeping does not.

## Accessibility
- Maintain readable contrast.
- Support system font scaling.
- Use semantic labels and logical focus order.
- Provide text labels with icons.
- Show validation errors next to the relevant field.
- Do not use color as the only status indicator.

## Offline behavior
- Core customer, ledger, transaction and report data must remain usable offline.
- Android local persistence: SQLite.
- Web local persistence: IndexedDB.
- PWA shell and essential assets are cached for offline launch.
- No mandatory login, cloud API or network call for core operations.
- Android and Web local databases are separate in v1.0; transfer is explicit through backup/restore.
