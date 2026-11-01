# Navigation and Responsive Layout

## Android navigation
Bottom navigation:
1. Home
2. Customers
3. Add (central primary action)
4. Reports
5. Settings

Use a back arrow on secondary screens. The Add action opens a clear choice between Udhar and Jama, then the transaction form.

## Web navigation
- Desktop: persistent left sidebar with Home, Customers, Add Transaction, Reports and Settings.
- Top bar: business name, global search and contextual actions.
- Tablet: collapsible sidebar.
- Mobile PWA: bottom navigation matching Android where practical.

## Responsive breakpoints
- Small mobile: 320–359 px.
- Standard mobile: 360–430 px.
- Large mobile: 431–599 px.
- Tablet: 600–1023 px.
- Desktop: 1024 px and above.

## Responsive rules
- No horizontal page overflow.
- Customer tables become stacked cards on narrow screens.
- Forms use one column on mobile and may use two columns on wide screens.
- Keep balance and primary transaction actions visible without excessive scrolling.
- Respect device safe areas and keyboard insets.
- Touch targets at least 44 × 44 CSS px.
- Do not rely on hover for essential actions.
- Use accessible labels for icon-only controls.
