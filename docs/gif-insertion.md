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

The keyboard **already** has a full rich-content insertion pipeline that
delegates picking to an `Activity` answering a public intent and replying by
broadcast (see [ARCHITECTURE.md](../ARCHITECTURE.md) → *Media-insertion
pipeline*). GIF search slots into the **provider** step only:

- **No keyboard-core changes.** `AnySoftKeyboardMediaInsertion`, `RemoteInsertionImpl`,
  `LocalProxy`, and `commitContent()` already handle request → URI → insert.
- **Networking stays out of the IME process** (an `Activity`, not the
  `InputMethodService`), which avoids ANRs and keeps typing latency clean.
- The provider activity today,
  [RemoteInsertionActivity.java](../ime/remote/src/main/java/com/anysoftkeyboard/remote/RemoteInsertionActivity.java),
  just calls `ACTION_PICK`. We swap that one screen for a GIF search screen.

## Scope of changes

All work lands in **`:ime:remote`** (plus a settings screen in `:ime:prefs` and
strings/keys). The keyboard service chain is unchanged.

```
ime/remote/src/main/java/com/anysoftkeyboard/remote/
  RemoteInsertionActivity.java        (modified) launch GifSearchActivity instead of ACTION_PICK
  gif/
    GifSearchActivity.java            (new) search UI; returns a content:// Uri via the existing broadcast
    GifSource.java                    (new) interface: search(query) -> List<GifResult>, download(result) -> File
    GipheryGifSource.java             (new) giphery REST client
    GiphyGifSource.java               (new) GIPHY HTTP API client
    GifResult.java                    (new) {previewUrl, fullGifUrl, mimeType, width, height, id, sourceId}
    GifRepository.java                (new) query enabled sources in priority order, first non-empty wins
    config/
      GifSourceConfig.java            (new) one configured source: id, type, label, enabled, position, + type fields
      GifSourceType.java              (new) enum GIPHERY | GIPHY (extensible)
      GifSourceConfigStore.java       (new) persist the ordered list as JSON; CRUD + reorder + enable/disable
      GifSourceFactory.java           (new) GifSourceConfig -> GifSource instance
    auth/GipheryTokenStore.java       (new) persist + refresh giphery JWTs (per giphery source)

ime/prefs/src/main/java/.../gifsources/
  GifSourcesFragment.java             (new) the reorderable list screen (RecyclerView + ItemTouchHelper)
  GifSourcesAdapter.java              (new) row binding, drag handle, enable switch
  GifSourceEditFragment.java          (new) add/edit one source (type-specific form)
```

The provider already declares its intent-filter and a `FileProvider`
([AndroidManifest.xml](../ime/remote/src/main/AndroidManifest.xml)); the new
activity reuses both. Downloaded GIF bytes are written to the module's
FileProvider cache, and the resulting `content://` Uri is handed back through
`BROADCAST_INTENT_MEDIA_INSERTION_AVAILABLE_ACTION` exactly as today — so
`LocalProxy` + `commitContent()` need no changes.

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
