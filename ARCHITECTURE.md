# Architecture

AnySoftKeyboard (this fork: **AnyGifKeyboard**) is a privacy-respecting Android
Input Method Editor (IME). This document maps the codebase so contributors — and
AI agents — can navigate it quickly. For build/lint/test/commit rules see
[AGENTS.md](AGENTS.md). For the GIF-insertion feature that defines this fork see
[docs/gif-insertion.md](docs/gif-insertion.md).

- **Package / application id:** `com.menny.android.anysoftkeyboard`
- **License:** Apache 2.0 (© 2009 Menny Even-Danan)
- **Min / Target / Compile SDK:** 23 / 35 / 36
- **Languages:** Java + Kotlin (Android), TypeScript (tooling)

## Build systems

Two build systems coexist; pick by what you are touching.

| System | Builds | Notes |
| ------ | ------ | ----- |
| **Gradle** (AGP 8.13.2, Kotlin 2.3.21, Java 21) | All Android code: `/ime/*`, `/addons/*`, `/api` | Use `assembleDebug`, **never** the `build` task. Module tests via `./gradlew :path:to:module:testDebugUnitTest`. |
| **Bazel** (bzlmod) | TypeScript tooling under `/js`, the Java emoji generators under `/emojis`, repo-wide format | `bazel test //...`, `bazel run //:format`. |

Android formatting/lint also runs through `./gradlew spotlessApply`. Auto-fixers
own formatting — don't hand-fix it.

## Top-level layout

| Path | Purpose |
| ---- | ------- |
| `/ime` | The keyboard app and its feature libraries (see below). |
| `/api` | Tiny public SDK shared with add-on APKs: `KeyCodes`, `MediaInsertion` intent contract. |
| `/addons` | Separately-packaged APKs: `languages/` (~50 packs), `themes/`, `quicktexts/` (emoji/quick-text). Each is a `pack` + `apk` pair discovered at runtime via broadcast. |
| `/emojis` | Bazel Java tools that generate emoji keyboard XML from Unicode data. |
| `/js` | Bazel TypeScript CI tooling (localization, dictionary updates, checkers). |
| `/gradle`, `/buildSrc`, `/tools`, `/config`, `/scripts` | Build config, custom Gradle plugins, lint/checkstyle config, CI scripts. |

## The IME application (`/ime`)

The service is assembled through a deep single-inheritance chain of mixins; each
layer adds one concern. From the framework up:

```
InputMethodService
  └─ AnySoftKeyboardBase
       └─ AnySoftKeyboardService            token / lifecycle
            └─ … (hardware, suggestions, etc.)
                 └─ AnySoftKeyboardHardware
                      └─ AnySoftKeyboardMediaInsertion   ← image/GIF commit pipeline
                           └─ AnySoftKeyboardWithQuickText   emoji / quick-text UI
                                └─ AnySoftKeyboard           concrete entry point
```

Main class: [AnySoftKeyboard.java](ime/app/src/main/java/com/anysoftkeyboard/AnySoftKeyboard.java).

### Key `/ime` modules

| Module | Role |
| ------ | ---- |
| `:ime:app` | The keyboard itself: service chain, keyboard views, suggestions, quick-text UI. |
| `:ime:base` / `:ime:base-rx` | Core keyboard infra and RxJava2 wrappers. |
| `:ime:base-test` | Robolectric test helpers (`AnySoftKeyboardRobolectricTestRunner`). |
| `:ime:remote` | **Media-insertion provider side** — picker activity + broadcast bridge (see below). |
| `:ime:fileprovider` | `LocalProxy`: copies a remote `Uri` into an app-private FileProvider `content://` Uri. |
| `:ime:addons` | Runtime discovery/loading of add-on APKs. |
| `:ime:dictionaries`, `:ime:nextword`, `:ime:gesturetyping` | Prediction, next-word, swipe typing. |
| `:ime:voiceime`, `:ime:overlay`, `:ime:prefs`, `:ime:pixel`, `:ime:chewbacca`, `:ime:notification`, `:ime:permissions`, `:ime:releaseinfo` | Voice input, theme overlay, settings UI, device-specific bits, etc. |

### Keyboard views & input

- Container/strip: [KeyboardViewContainerView.java](ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/KeyboardViewContainerView.java), suggestions in [CandidateView.java](ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/CandidateView.java).
- Key rendering/touch: [AnyKeyboardView.java](ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/AnyKeyboardView.java) over `AnyKeyboardViewBase`.
- Input events flow through [OnKeyboardActionListener.java](ime/app/src/main/java/com/anysoftkeyboard/keyboards/views/OnKeyboardActionListener.java).
- Text is committed to the target field via `InputConnection.commitText(...)` in [AnySoftKeyboardSuggestions.java](ime/app/src/main/java/com/anysoftkeyboard/ime/AnySoftKeyboardSuggestions.java).

### Quick-text / emoji panel

The emoji panel is the closest existing analogue to a GIF panel:
[AnySoftKeyboardWithQuickText.java](ime/app/src/main/java/com/anysoftkeyboard/ime/AnySoftKeyboardWithQuickText.java)
drives a `ViewPager` of grids
([QuickTextPagerView.java](ime/app/src/main/java/com/anysoftkeyboard/quicktextkeys/ui/QuickTextPagerView.java)),
whose action row already shows a **media-insertion button** when the target
field accepts images.

## Media-insertion pipeline (where GIFs plug in)

The keyboard already has an end-to-end rich-content path. It deliberately keeps
networking and pickers **out of the IME process**, delegating to an `Activity`
that answers a public intent and replies via broadcast.

```
 Quick-text media button
   → AnySoftKeyboardMediaInsertion.handleMediaInsertionKey()
   → RemoteInsertionImpl.startMediaRequest()           // ime/remote
        fires Intent  INTENT_MEDIA_INSERTION_REQUEST_ACTION  (mime types + requestId)
   → RemoteInsertionActivity                            // ime/remote — the PROVIDER
        picks media, returns a content Uri
        sends Broadcast  BROADCAST_INTENT_MEDIA_INSERTION_AVAILABLE_ACTION
   → RemoteInsertionImpl.MediaInsertionAvailableReceiver
   → LocalProxy.proxy()                                 // ime/fileprovider — copy to local Uri
   → AnySoftKeyboardMediaInsertion.onMediaInsertionReply()
   → InputConnectionCompat.commitContent()             // insert into the editor
```

Contract: [api/MediaInsertion.java](api/src/main/java/com/anysoftkeyboard/api/MediaInsertion.java).
Target-app capability is detected in
[AnySoftKeyboardMediaInsertion.onStartInputView()](ime/app/src/main/java/com/anysoftkeyboard/ime/AnySoftKeyboardMediaInsertion.java)
via `EditorInfoCompat.getContentMimeTypes()` (`image/*`, `image/gif`).

**The provider today** ([RemoteInsertionActivity.java](ime/remote/src/main/java/com/anysoftkeyboard/remote/RemoteInsertionActivity.java))
just launches a generic `ACTION_PICK` image chooser. The GIF feature replaces
that step with a giphery-backed (GIPHY-fallback) GIF search screen — the rest of
the pipeline and the keyboard core are untouched. See
[docs/gif-insertion.md](docs/gif-insertion.md).

## Add-on model (`/addons`)

Languages, themes, and quick-text packs are independent APKs. Each ships a
`PackBroadcastReceiverBase` subclass and XML resources; the core discovers them
at runtime through `:ime:addons`. New emoji/quick-text content is data, not code.

## Testing & CI

- **Java/Kotlin:** JUnit4 + Robolectric. Files end in `*Test.java` / `*Test.kt`. For `:ime:app`, filter with `--tests "fully.qualified.ClassName"`.
- **TypeScript:** the `node:test` runner; files end in `*.test.ts`.
- **CI** ([.github/workflows/checks.yml](.github/workflows/checks.yml)) runs `bazel build //...`, `bazel coverage`, `bazel run //:format`, sharded `./gradlew testDebugUnitTest`, and static analysis via `scripts/ci/ci_check.sh`.
