# Agent instructions

Sauron turns Thai payment slip screenshots into categorized expenses.
Everything about the product's intent lives in `docs/architecture.md`;
its phase checks define done. Assume every contributor sees only the
files it opened, copies the nearest example, and takes the shortest path
that compiles. The repo is structured so that path is the right one.

## Build, test, verify

- Unit tests: `./gradlew testDebugUnitTest`
- Build: `./gradlew assembleDebug`
- Install on the connected device or emulator: `./gradlew installDebug`
- A phase is done when its check passes on a device, not when the code compiles.

## Rule table

Each rule pairs with what enforces it. When a correction happens, fix the
mistake and add the rule in the same change. Rules marked review rely on
reviewers and agents; if a review rule fires twice, promote it to a test.

| Rule | Enforced by |
| --- | --- |
| One baht-string parser: `SlipAmount` | review on `replace(",", "")` outside `SlipAmount.kt` |
| No capture-status string literals in DAO SQL; bind parameters | `DaoStatusLiteralTest` |
| The Gemma retry cap exists once, in `CaptureStatus` | review on `gemmaAttempts` compared to a literal |
| Slip text lives in `testdata/`, never inline in tests | review on triple-quoted slip strings in `app/src/test` |
| One expense write: `ExpenseFiler` | review on `expenseDao.insert` outside `ExpenseFiler.kt` |
| Logging carries counts, ids, and uris; never slip text | review on `Log.` outside `RunLog.kt` and `GemmaSlipReader.kt` |
| Tesseract is imported only in `TessOcr.kt` | review on `tesseract` outside `TessOcr.kt` |
| Doc numbers match the build files | `DocSyncTest` |
| Doc claims carry a source in `docs/facts.md` | `DocsLintTest` |
| New external facts land in `docs/facts.md` with a source and date | judgment call, no check |
| Do not build a parked idea without unparking it first | judgment call, no check |

## Parked ideas

`docs/architecture.md` ends with the parking lot. Anything there stays
unbuilt until its revisit trigger fires, and a commit that builds one
must move it out of the lot in the same change.
