@AGENTS.md

# AnyGifKeyboard fork

This fork adds **in-keyboard GIF search and insertion**. Before working on
keyboard internals, read [ARCHITECTURE.md](ARCHITECTURE.md); before touching the
GIF feature, read [docs/gif-insertion.md](docs/gif-insertion.md).

Key facts for agents:

- GIF **sources are a user-maintained, reorderable list** in settings, queried in
  priority order (first non-empty wins). Two types ship: self-hosted **giphery**
  (the user's own library, https://github.com/ApexAlphaHero/giphery — FastAPI, JWT
  auth) and the public **GIPHY HTTP API**. (Names collide on purpose: *giphery* =
  self-hosted; *GIPHY* = public service.) The list/order/enabled-state is stored
  as JSON in `SharedPreferences` — never hardcode keys or sources in source.
- Use a GIPHY **API** key (plain REST), **not** the GIPHY Android SDK. The SDK is
  a heavy drop-in UI library that bypasses our pipeline and counts as a new
  third-party dependency — which needs approval per [AGENTS.md](AGENTS.md).
- GIF insertion reuses the **existing media-insertion pipeline**; do not add
  networking to the `InputMethodService`. New work lands in `:ime:remote`
  (provider activity), `:ime:prefs` (settings), and strings/keys — the keyboard
  service chain stays unchanged.
- Default to the **no-new-dependency** path (`HttpURLConnection` + `org.json`,
  platform `ImageDecoder`) unless dependency additions are explicitly approved.
