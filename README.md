# ANB Khata Book v1.0

Premium offline-first digital bahi-khata for a single business, targeting Android APK and Web PWA.

## Phase 1 status
UI/UX design specification prepared. This package contains design documentation only; it does not contain application code, a runnable app, or test results.

## Approved product decisions
- Product: ANB Khata Book
- Platforms: Android APK + Web PWA
- Business model: Single business
- Architecture: Offline-first; no mandatory cloud/server dependency
- Visual theme: Royal Blue + Royal Gold + Premium White
- Customer sharing: WhatsApp share intent/link with editable preview; user manually confirms sending
- Development workflow: Android + Termux, push directly to `main`, GitHub Actions builds Android and Web artifacts
- Phase testing policy: Test only after all deliverables for the current phase are complete

## Phase 1 deliverables
See `docs/`:
- `design-system.md`
- `screen-specifications.md`
- `navigation-and-responsive.md`
- `ux-and-accessibility.md`
- `phase-1-checklist.md`

## Important behavior
Android and Web use separate local databases (SQLite and IndexedDB respectively). They share business rules and UI conventions, but do not automatically synchronize. Moving data between them is through explicit backup/restore in v1.0.

## Next phase
Phase 2: project foundation and repository scaffold. Do not start Phase 2 until Phase 1 documentation is reviewed and approved.
