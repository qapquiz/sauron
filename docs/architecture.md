# Sauron architecture

## What Sauron is

Sauron is an Android app that turns Thai bank money alerts into categorized
expenses. Thai banks push a notification for every money movement, and a
transfer in a Thai banking app ends with a payment slip on screen. Sauron
captures the alerts in the background, reads the slips with OCR, extracts the
amount, the merchant, and the time, and files each expense under a category.

The app is local only, with no account, no server, and no bank login.

Phase 1 is the shell, and it is what this scaffold delivers. It sets up the
build, the storage schema, and manual dependency wiring, and it captures
nothing yet. Everything else lands in phases 2 through 6.

## Stack choices

| Choice | Version | Why |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | current release, with Kotlin support built in |
| Kotlin | 2.2.10 | built into AGP 9.4.1 |
| Gradle | 9.8.1 | current release |
| JDK | 17 | required by the Android toolchain |
| compileSdk | 37 | required by the Compose BOM's ui 1.12.1 AAR metadata |
| minSdk | 26 | covers more than 95 percent of devices in Thailand, more below |
| Jetpack Compose with Material 3 | compiler plugin 2.2.10 | declarative screens in Kotlin, current Material components |
| Room | 2.8.5 | on-device SQL storage, schema exported to app/schemas |
| KSP | 2.3.12 | runs Room's annotation processing at build time |
| Gemma 4 E2B | E2B-it QAT, LiteRT-LM | on-device extraction and categorization, Apache 2.0, more below |

minSdk is 26. From that level the app gets notification access and the
current AndroidX stack, and API 26 is Android 8.0 from 2017, so the
compatibility code for older devices stays small.

kotlinx.serialization, WorkManager, and navigation are not in the build yet.
Each lands in the phase that first needs it, so the build carries no unused
dependency.

The model plan splits reading from understanding. ML Kit text recognition
reads the slip, because it is fast, deterministic, and works offline. Gemma 4
E2B understands what was read. It runs on device through the official
LiteRT-LM build (the GPU variant is a 2.01 GB download, measured from the
litert-community release), it ships under Apache 2.0, and it keeps financial
data off any server. The parser handles slips whose text is already
structured, and the model handles the messy tail, so a slow path never runs
when a fast one is certain.

Google's Gemini API also serves Gemma 4 free of charge, with no paid tier,
but free tier requests may be used to improve Google's products. Sauron never
ships an API key and never sends slip data anywhere by default. The API is an
opt-in fallback only: a user who accepts that trade pastes their own AI
Studio key, and slip text then leaves the phone for categorization.

An AppContainer on the Application class wires dependencies by hand, and each
screen takes what it needs from the container. Hilt was rejected because one
class consumes the container, so code generation would add build time without
removing work. Revisit when a second consumer appears.

## Data model

Money is stored as minor units. An expense keeps its satang amount in a Long
and never in a float or a double, so 12.50 THB is stored as 1250 and arithmetic
on it stays exact. Currency is a closed enum that starts with THB, so adding a
second currency is a compile-checked change instead of a new string column.

Slip provenance is a sealed interface with three variants, a bank
notification, a screen capture, and manual entry. Because the interface is
sealed, the compiler rejects any consumer that misses a variant, so a fourth
source breaks the build until each consumer handles it.

Bank identification is a registry lookup, not conditionals scattered through
the capture code. The registry maps a bank app package name to a bank. An
unknown package name returns null today, and confirming the real package names
of the Thai bank apps is phase 2 work.

Room 2.8.5 stores everything on disk. The build exports the Room schema, and
the exports are committed under app/schemas. A schema change lands in review as
a readable diff, and tests can verify a future migration against the exports.

## Phases

A phase is done when its check passes on a device, not when the code compiles.

- **Phase 2, capture.** A NotificationListenerService binds to the bank
  notification packages. The app asks for POST_NOTIFICATIONS and the listener
  binding on first run, and WorkManager picks up each alert as a job. The check
  is that a test bank notification produces a pending capture row on a device.

- **Phase 3, slip OCR.** The app ingests screen captures of payment slips and
  runs ML Kit text recognition on them, using the Thai script model
  com.google.mlkit:text-recognition-thai. The check is that a real Thai slip
  image yields parsed text lines.

- **Phase 4, extraction.** The parser turns recognized text into an amount, a
  merchant, and a datetime, and writes an ExpenseEntity row per slip. Slips
  the parser cannot confidently read go to Gemma 4 E2B as a fallback, with a
  JSON contract for the fields. The check is that a golden set of slips maps
  to the expected expenses, with the fallback rate counted.

- **Phase 5, categorization.** A rule table matches merchant keywords first,
  and Gemma 4 E2B categorizes what the rules miss. Thai quality of small
  on-device models is unmeasured, so the golden set decides whether E2B
  clears the accuracy target, or the app steps up to E4B (a 2.97 GB GPU
  build) or few-shot prompting before anything bigger is considered. The
  check is that the golden set categorizes against a measured accuracy
  target.

- **Phase 6, reporting.** The main screen shows monthly totals per category.
  The check is that the on-screen totals match a SQL query over seeded data.

The app icon and the brand assets are deferred, as are the GitHub remote and
CI. Nothing in phases 2 through 6 depends on them.
