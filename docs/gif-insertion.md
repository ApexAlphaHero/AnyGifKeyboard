# GIF insertion (user-managed source list)

This fork (**AnyGifKeyboard**) adds in-keyboard GIF search and insertion. GIF
**sources are a user-maintained, reorderable list** in settings. Each source is
queried in priority order until one returns results, so the user controls both
*which* providers are used and *which wins first*.

Two source types ship initially:

- **giphery** — a self-hosted [giphery](https://github.com/ApexAlphaHero/giphery)
  instance (FastAPI + PostgreSQL, the user's own GIF library; JWT auth).
- **GIPHY** — the public [GIPHY HTTP API](https://developers.giphy.com/docs/api/).

(Name collision is intentional: **giphery** = the user's self-hosted server;
**GIPHY** = the public service.) A typical setup is one giphery source on top and
one GIPHY source beneath as fallback, but the list is fully user-defined — any
number of sources, any order, each independently enabled/disabled. The design is
open for more types later (e.g. Tenor) by adding a new `GifSource` impl.

> **Use a GIPHY _API_ key, not an SDK key.** We make plain REST calls and render
> results in our own `GifSearchActivity`, so we need only an HTTP API key
> (GIPHY dashboard → Create an App → **API**, not **SDK**). The GIPHY **Android
> SDK** is a heavyweight drop-in UI library (its own grid + networking,
> bundle-id-locked key) that would bypass this pipeline and add a large
> third-party dependency requiring approval per `AGENTS.md`. Avoid it.

## Why this design

GIF search is an **inline keyboard panel** docked in the keyboard container —
the same place the emoji/quick-text panel shows — so the experience matches
Gboard (you never leave the keyboard). Tapping the media key
(`KeyCodes.IMAGE_MEDIA_POPUP`) shows the panel; tapping a GIF commits it and
returns to the keyboard.

- **Reuses the commit path.** The chosen GIF is downloaded to the app-private
  `media/` folder and committed via
  `AnySoftKeyboardMediaInsertion.commitGifContent()` → `commitContent()` — the
  same rich-content API the keyboard already used. The receiving app only needs
  to advertise `image/gif` (the media button only appears when it does).
- **Networking runs in the IME process.** Unlike most of the keyboard, the panel
  does HTTP — but **always off the main thread** (RxJava `background`/`mainThread`
  schedulers), so typing latency/ANRs are unaffected. This is a deliberate
  exception to the usual "no networking in the `InputMethodService`" rule, taken
  to get the inline Gboard-style UX. (An earlier iteration used a separate
  full-screen activity to keep networking out of the IME; the inline panel
  replaced it by request.)
- **Upstream provider untouched.**
  [RemoteInsertionActivity.java](../ime/remote/src/main/java/com/anysoftkeyboard/remote/RemoteInsertionActivity.java)
  and the `INTENT_MEDIA_INSERTION_REQUEST_ACTION` infra are left as-is; the GIF
  feature simply doesn't route through them.

## Scope of changes

GIF search logic lives in **`:ime:remote`** (`com.anysoftkeyboard.remote.gif`);
the panel view and settings screen live in **`:ime:app`**.

```
ime/remote/src/main/java/com/anysoftkeyboard/remote/gif/
  GifSource.java            interface: search(query) -> List<GifResult>, download(url) -> bytes
  GipheryGifSource.java     giphery REST client (bearer auth, refresh-on-401)
  GiphyGifSource.java       GIPHY HTTP API client
  GifResult.java            {id, previewUrl, fullGifUrl, mimeType, width, height, sourceId}
  GifRepository.java        query enabled sources in priority order, first non-empty wins
  GifSourceFactory.java     GifSourceConfig -> GifSource
  GifCache.java             save downloaded bytes -> FileProvider content:// Uri
  net/HttpTransport.java + UrlConnectionHttpTransport.java   injectable HTTP (testable)
  ui/GifResultsAdapter.java + GifImageLoader.java            staggered grid + platform GIF decode
  config/GifSourceConfig.java, GifSourceType.java, GifSourceConfigStore.java
  auth/GipheryTokenStore.java                                refresh + persist giphery JWTs

ime/app/src/main/java/com/anysoftkeyboard/
  gif/GifSearchPanelView.java                 the inline panel (search box, chips, staggered grid)
  ime/AnySoftKeyboardWithQuickText.java        (modified) handleMediaInsertionKey() -> show/cleanup panel
  ime/AnySoftKeyboardMediaInsertion.java       (modified) commitGifContent(uri, mimeTypes)
  ui/settings/gifsources/GifSourcesFragment.java + GifSourcesAdapter.java   reorderable sources list
```

The keyboard shows/removes the panel exactly like the emoji panel: hide the
keyboard view, `addView` the panel into the `KeyboardViewContainerView`, and
remove it on selection/close/finish-input. The `FileProvider` declared in
[ime/remote AndroidManifest.xml](../ime/remote/src/main/AndroidManifest.xml)
(authority = app id, path `media/`) serves the cached GIF Uri.

## giphery source

Base URL is user-configured (their SWAG/HTTPS domain). API per giphery's
ARCHITECTURE.md:

- **Search:** `GET /api/v1/gifs?q={query}&limit={n}&cursor={cursor}`
  → `{ "items": [ { "id", "mime_type", "width", "height", "tags",
  "raw_url": "/api/v1/gifs/{id}/raw", ... } ], "next_cursor": ... }`
- **Raw bytes:** `GET /api/v1/gifs/{id}/raw` → `image/gif` binary.
- **Auth:** `Authorization: Bearer <access JWT>` on every call. Access tokens are
  short-lived (~15 min); refresh via `POST /auth/refresh` (rotating refresh
  token). First-time pairing uses `POST /invites/redeem` (no prior auth) to mint
  the initial token pair.

`GipheryTokenStore` holds the refresh token in `EncryptedSharedPreferences`,
transparently refreshes the access token on `401`, and surfaces a clear
"re-pair" error if the refresh token is rejected.

## GIPHY source

- **Search:** `GET https://api.giphy.com/v1/gifs/search?api_key={API_KEY}&q={query}&limit={n}&rating=g`.
- Response: `data[]` with `images.fixed_width.url` (or `preview_gif.url`) → preview
  and `images.original.url` → full GIF.
- Requires a GIPHY **API** key (the REST key — *not* the Android SDK; see the note
  at the top), entered per-source in settings.
- GIPHY image URLs are public CDN links, so raw download needs no auth header.

## Source priority & querying (`GifRepository`)

`GifSourceConfigStore` returns the **enabled** sources in user-defined order.
`GifRepository.search(query)`:

1. Iterates sources in priority order, each with a short timeout (e.g. 4s).
2. Returns the **first source that yields results**; on empty/error/timeout it
   advances to the next source.
3. If every source is exhausted with nothing, shows an empty-state message.

Each `GifResult` carries its originating `sourceId` so the UI can badge which
source a GIF came from, and so download uses the right transport (giphery needs
the bearer header; GIPHY URLs are public CDN links needing none).

> Default behavior is *first non-empty wins* (true priority). A future "merge all
> enabled sources" toggle is possible but out of scope for v1.

## Settings — the GIF sources list (`:ime:prefs`)

A new **"GIF sources"** screen, opened from the keyboard settings. It is a
`RecyclerView` list (not a static `PreferenceScreen`) because rows are
user-managed and reorderable:

- **Reorder** — drag handle per row via `ItemTouchHelper`; row order *is* the
  search priority. Persisted on drop.
- **Enable/disable** — a switch per row; disabled sources are skipped by
  `GifRepository` but kept in the list.
- **Add** (`+`) — choose a type (giphery / GIPHY), then a type-specific form.
- **Edit / delete** — tap a row to edit; overflow or swipe to delete.

Type-specific forms:

- **giphery:** display label, base URL, and pairing — paste an invite token →
  `POST /invites/redeem` → store JWTs in `GipheryTokenStore`. Shows paired state.
- **GIPHY:** display label + **API key** (the REST key). The key is entered here
  at runtime and saved to `SharedPreferences` — never hardcoded or committed.

The list persists as an ordered JSON array via `GifSourceConfigStore`. Gate the
keyboard's media-button copy to read "GIF" when at least one source is enabled.

## Dependencies — needs approval

`AGENTS.md` forbids new third-party dependencies without approval. Two options:

- **No new deps (recommended default):** `HttpURLConnection` + `org.json`
  (already on Android) for REST; render GIF previews with the platform
  `ImageDecoder`/`AnimatedImageDrawable` (API 28+) and a static-frame fallback
  for API 23–27. More code, zero new libraries.
- **With deps (needs sign-off):** OkHttp for HTTP + Glide/Coil for animated GIF
  grid loading. Much less code; adds libraries to `:ime:remote`.

**Decision required before implementation.** Until then, the spec assumes the
no-new-deps path.

## Testing

Follow the project's Robolectric conventions (`*Test.java`, run with
`./gradlew :ime:remote:testDebugUnitTest`):

- `GifRepositoryTest` — priority matrix with fake sources: first-source-hit,
  first-empty→second, first-error/timeout→second, all-empty, disabled-skipped,
  single-source, ordering respected.
- `GifSourceConfigStoreTest` — JSON round-trip; add/edit/delete; reorder persists
  order; enable/disable flag; returns only enabled in order.
- `GipheryGifSourceTest` — search JSON parsing; `401` triggers one refresh then
  retry; refresh-failure surfaces re-pair error. Use a fake HTTP transport.
- `GiphyGifSourceTest` — `data[].images` mapping; missing-key short-circuit.
- `GipheryTokenStoreTest` — persist/refresh/rotate; reject handling.
- Reuse existing `RemoteInsertionImplTest` as the template for asserting the
  broadcast round-trip still wires into `commitContent()`.

## End-to-end verification

1. `./gradlew :ime:app:assembleDebug` (build runs cleanly under WSL).
2. Install on a device/emulator; enable AnyGifKeyboard.
3. In an app that accepts images (e.g. a chat/compose field — the media button
   only shows when `EditorInfo` advertises `image/gif`), open the quick-text
   panel and tap the media/GIF button.
4. In **GIF sources**, add a giphery source (pair it) and a GIPHY source (paste
   your API key); drag giphery above GIPHY.
5. Search a term in your giphery library → confirm a giphery result inserts.
6. Search a nonsense term → confirm the GIPHY fallback (badge differs) inserts.
7. Drag GIPHY above giphery, or disable giphery → confirm priority changes which
   source answers.
