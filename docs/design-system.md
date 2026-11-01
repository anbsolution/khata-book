# ANB Khata Book — Design System v1.0

## 1. Brand direction
Premium, trustworthy, clean and practical bookkeeping UI. Prioritize legibility of amounts and fast entry over decorative effects.

## 2. Color tokens

| Token | HEX | Usage |
|---|---|---|
| `primary` | `#174EA6` | Primary buttons, active navigation |
| `primary-dark` | `#102C55` | Headers, sidebar, dark surfaces |
| `gold` | `#D4AF37` | Premium highlights, central add action |
| `gold-soft` | `#F5E7B2` | Subtle highlight surfaces |
| `background` | `#FFFFFF` | Main background |
| `surface` | `#F7F8FC` | Cards and secondary surfaces |
| `success` | `#16834A` | Payment received, positive status |
| `danger` | `#C62828` | Outstanding balance, validation errors |
| `text` | `#17243A` | Main text |
| `muted` | `#667085` | Secondary text, hints |

## 3. Typography
- Hindi: Noto Sans Devanagari.
- English and numerals: Inter, with tabular numerals for monetary values.
- Page title: 20–24 px, semibold/bold.
- Section title: 16–18 px, semibold.
- Body: 14–16 px.
- Supporting text: 12–13 px.
- Do not communicate credit/debit status by color alone; pair color with text and icon.

## 4. Components
- App header with page title and contextual actions.
- Balance summary card.
- Customer list row/card.
- Transaction row with type, date, amount and note.
- Search field and filter chips.
- Primary, secondary and destructive buttons.
- Amount input with INR prefix.
- Date selector.
- Confirmation dialog.
- Toast/status message.
- Empty, loading and error states.
- WhatsApp message preview.
- Backup/restore confirmation panels.

## 5. Component behavior
- Touch targets: minimum 44 × 44 CSS px.
- Buttons show pressed, disabled and loading states.
- Forms show inline validation.
- Destructive actions require confirmation.
- Financial values use Indian grouping, e.g. ₹1,25,450.
- Amount entry must reject zero, negative and invalid values.
- Avoid tiny gold-on-white text; use dark navy text on gold surfaces.

## 6. Iconography
Use a consistent rounded line-icon family. Pair important icons with labels, especially Udhar, Jama, Share, Backup and Restore.

## 7. Surface and elevation
- White and pearl-white surfaces.
- Thin neutral borders.
- Soft, restrained shadows.
- Gold is an accent, not a large background on every screen.
- Avoid excessive gradients and ornamental decoration that reduces readability.
