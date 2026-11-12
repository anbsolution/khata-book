# Development Rules — Khata Book

## 1. GitHub-First Code Review
- Review source code directly from the connected GitHub repository.
- Fetch the actual files from the correct branch before reviewing.
- Do not repeatedly ask the user for source-code screenshots.
- Do not treat screenshots or partial terminal output as a substitute
  for reviewing the repository source.

## 2. Local Changes and GitHub
- Clearly distinguish local uncommitted code from GitHub code.
- Never claim that local changes have been reviewed through GitHub.
- If GitHub cannot access a file or permission is denied, explain
  the limitation clearly.
- Use Termux Git push when GitHub integration cannot write.

## 3. Build and Test Verification
- Verify actual GitHub Actions workflow runs and job results.
- Inspect failure logs before proposing fixes.
- Never claim a build, test, APK, or deployment succeeded without
  checking its actual result.
- Report unverified items as pending.

## 4. Repository Safety
- Preserve the existing repository name and Git history.
- Work directly on the main branch.
- Do not create a new branch unless explicitly authorized.
- Keep a verified backup before major migrations.
- Do not overwrite unrelated user changes.

## 5. User Workflow
- Prefer Android mobile + Termux instructions.
- Do not require the user to use nano.
- Do not ask for code screenshots when GitHub source review is possible.
- Give short, copy-pasteable commands and request only necessary output.
