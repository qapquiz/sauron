# Sauron architecture

## What Sauron is

Sauron is an Android app that turns Thai payment slips into categorized
expenses. A transfer in a Thai banking app ends with a payment slip on
screen. People screenshot those slips to share them, and bank apps can also
save the slip itself into a folder of its own in the phone's Pictures
directory. Sauron watches those bank-saved slip folders in the background,
reads them with OCR, extracts the amount, the merchant, and the time, and
files each expense under a category.

The app is local only, with no account, no server, and no bank login.

Phase 1 is the shell, and it is what this scaffold delivers. It sets up the
build, the storage schema, and manual dependency wiring, and it captures
nothing yet. Everything else lands in phases 2 through 5.

## Stack choices

| Choice | Version | Why |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | current release, with Kotlin support built in |
| Kotlin | 2.2.10 | built into AGP 9.4.1 |
| Gradle | 9.8.1 | current release |
| JDK | 17 | required by the Android toolchain |
| compileSdk | 37 | required by the Compose BOM's ui 1.12.1 AAR metadata |
| minSdk | 26 | API 26 is Android 8.0 from 2017, so the older-device compatibility code stays small |
| Jetpack Compose with Material 3 | compiler plugin 2.2.10 | declarative screens in Kotlin, current Material components |
| Room | 2.8.5 | on-device SQL storage, schema exported to app/schemas |
| KSP | 2.3.12 | runs Room's annotation processing at build time |
| Gemma 4 E2B | E2B-it QAT, LiteRT-LM | on-device extraction and categorization, Apache 2.0, more below |

minSdk is 26. From that level the app gets MediaStore access and the current
AndroidX stack, and API 26 is Android 8.0 from 2017, so the compatibility
code for older devices stays small.

kotlinx.serialization, WorkManager, and navigation are not in the build yet.
Each lands in the phase that first needs it, so the build carries no unused
dependency.

The model plan splits reading from understanding. Tesseract reads the slip,
because it is fast, deterministic, and works offline. Google's ML Kit ships
no Thai text recognizer at all, a fact verified against its maven group
index, so the reading engine is Tesseract4Android with the fast Thai and
English models bundled in the app, about 5 MB combined. Gemma 4 E2B
understands what was read. It runs on device through the official
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

Slip provenance is a sealed interface with two variants, a screen capture
and manual entry. Because the interface is sealed, the compiler rejects any
consumer that misses a variant, so a third source breaks the build until each
consumer handles it.

Watched images come from folders that bank apps save slips into, so their
origin already says slip. The content score chooses the path, not admission.
A high score goes to the text parser, and a low one goes to Gemma. The rule
table is pure Kotlin with JVM tests.

Room 2.8.5 stores everything on disk. The build exports the Room schema, and
the exports are committed under app/schemas. A schema change lands in review as
a readable diff, and tests can verify a future migration against the exports.

## Phases

A phase is done when its check passes on a device, not when the code compiles.

- **Phase 2, slip ingestion.** A WorkManager job watches the bank-saved
  slip folders through MediaStore and runs Tesseract OCR on new images with
  the bundled Thai and English models. The watched folders are
  Pictures/CLICX, Pictures/K PLUS, and Pictures/SCB EASY. The WatchedFolder
  registry lists them with the bank whose app saves into each. Every image
  in a watched folder was saved by a bank app, so the content score no
  longer decides whether something is a slip. It splits the fast parse path
  from the Gemma path. The check for the original screenshot watch passed
  on 2026-10-11 with three fixture slips captured and two negative
  screenshots rejected.

  On 2026-10-23 the watch moved from Pictures/Screenshots to the three bank
  folders, after the operator saw the K PLUS app save a slip into
  Pictures/K PLUS on their own phone. The RELATIVE_PATH match is
  exact-or-slash, which removes the prefix over-match a plain LIKE pattern
  had against sibling folder names. CLICX joined the Bank enum as
  Thailand's first virtual bank, licensed in May 2026. The on-device check
  for the new folders is pending. Save a slip from each of the three bank
  apps and confirm a capture row per slip.

- **Phase 3, extraction.** The parser turns recognized text into an amount, a
  merchant, a datetime, and the issuing bank named on the slip, and writes an
  ExpenseEntity row per slip with screen capture provenance. Two OCR quirks
  are inputs to design against, both measured in the phase 2 device run: the
  baht sign often reads as the digit 8, and sara am sometimes decomposes into
  its base marks. A labeled amount starting with 8 is parked as unparsed
  rather than guessed, because a true ฿8,350.00 is equally plausible. Slips
  the parser cannot confidently read stay at NO_PARSE with the reasons
  logged, and the Gemma fallback arrives in phase 4. The check is that a
  golden set of slips maps to the expected expenses, with the fallback rate
  counted. The golden set passed on 2026-10-12: four of seven cases parse,
  three park with precise reasons. On device, one of three fixture slips
  parses end to end (the Kasikorn slip, ฿1,250.00 with the bank recorded)
  and the other two park with measured reasons, so roughly two thirds of
  current OCR output is Gemma's job. Tuning measured along the way:
  tessdata_best beats tessdata_fast, PSM single column or single block tie
  and beat the default, and a 2x upscale made accuracy worse and was
  rejected.

  A real K PLUS bill payment slip set the phase 4 agenda on 2026-10-12: a
  decorative watercolor design scored 0 to 3 of the needed 4 under every
  OCR configuration tried, so the pipeline refused to store it, which is
  correct behavior. Reading real decorative slips is Gemma's job. The slip
  also added two parser features for its format: two digit Buddhist Era
  years such as 69 meaning 2569, and merchants named on an unlabeled
  company line ending in จำกัด. The image is kept under testdata as the
  first real fixture.

- **Phase 4, categorization.** A rule table matches merchant keywords first,
  and Gemma 4 E2B handles what the rules and the parser could not: borderline
  and parser-failed slips go to Gemma with the original image, not the OCR
  text, because real decorative slips defeat OCR entirely. Done on
  2026-10-12: the real K PLUS slip from this document's first real fixture
  became an expense on device, แมคโดนัลด์ ฿49.00, Food & Drink, bank
  Kasikornbank, with Gemma reading the image directly under litertlm 0.16.1
  (newer builds carry Kotlin 2.4 metadata our compiler cannot read).
  Unclassified slips stay at NO_PARSE and are retried, which is correct but
  unbounded; an attempt counter is phase 5 polish. The Gemma stage skips
  itself while no model is on the phone, so the attempt budget is spent
  only when a model exists. The model arrives through an in-app download:
  a card on the main screen starts a WorkManager job that fetches the 2.0 GB
  GPU file from the litert-community release over an unmetered network,
  runs as a dataSync foreground service, and writes it to the location the
  reader already searches. The download verified end to end on an emulator
  on 2026-10-23.

- **Phase 5, reporting.** The main screen opens with This month, one total
  per category for the current calendar month at Bangkok time, over the
  expense list, and a photo permission card appears when access is missing.
  The check is that the on-screen totals match a SQL query over the data.
  The check passed on 2026-10-13: the screen showed Other ฿1,250.00 and
  Food & Drink ฿49.00 against the identical SQL totals on an upgraded
  device. Failed Gemma reads also cap at three attempts per capture, so
  hopeless decorative slips stop costing battery.

Notification capture is parked. Listening to bank notifications would also
catch spending that never produces a viewed slip, but it needs listener
permissions, verified bank app package names, and OEM battery exceptions.
Revisit only after the storage pipeline is complete.

The app icon and the brand assets are deferred. The GitHub remote and CI
have existed since October 2026.
