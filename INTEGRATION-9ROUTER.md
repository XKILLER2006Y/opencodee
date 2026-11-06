# 9Router integration

Adds a second local server to the app: the [9Router](https://github.com/decolua/9router) AI gateway runs inside the same Ubuntu proot environment as `opencode serve`, on its own port (default 20128), with its own foreground service.

## What was added

### Runtime
- `runtime/NineRouterController.kt` — downloads the official Node.js 22 arm64 tarball and the published `9router` npm tarball on the Android side, verifies them (SHA-256 from nodejs.org, SHA-512 from the npm registry), unpacks them with `TarExtractor`, copies `bin/node` and the package's `app/` folder into the rootfs, then runs `node /opt/9router/app/custom-server.js` with `PORT`, `HOSTNAME=127.0.0.1`, `DATA_DIR=/root/.9router`, `INITIAL_PASSWORD` and `JWT_SECRET`. Supervises it (health poll on `/api/health`, watchdog, restart with backoff), tracks its log, and keeps the dashboard password and JWT secret in `Prefs`.
- `runtime/NineRouterService.kt` — foreground service that keeps the gateway alive in the background, next to `ServerService` (separate notification channel and ID).

### Data
- `data/Prefs.kt` — `ninerouterPort`, `ninerouterAuto`, `ninerouterVersion`, plus lazily generated `ninerouterPassword` / `ninerouterJwt` (same pattern as `serverPassword`).
- `data/CustomProviders.kt` — `NINEROUTER(port)` preset: OpenAI-compatible base URL `http://127.0.0.1:<port>/v1`, free Kiro models as fallback list. It goes through the existing `connect()` path (test message + `/models` fetch + provider block in opencode config).

### UI
- `ui/screens/NineRouterScreen.kt` — new screen (`Route.NineRouter`): status, Install / Start / Stop / Restart, log, dashboard password with copy, "Open dashboard" (WebView), "Start with the app" switch, docs link.
- `ui/screens/MoreScreens.kt` — "9Router server" row in Settings → Local server.
- `ui/screens/ProvidersScreen.kt` — "9Router" row under Add provider with a sheet: install/start hint, Open dashboard, API key field → `CustomProviders.connect()`.
- `ui/Nav.kt`, `ui/Root.kt`, `ui/theme/Icons.kt` — route, dispatch, router icon.
- `MainActivity.kt` — restart 9Router on resume when `ninerouterAuto` is on.

### Wiring
- `OcApp.kt` — `Graph.ninerouter = NineRouterController(app, paths, prefs)`.

## Flow

1. Settings → Local server → 9Router server → Install. The app downloads Node.js 22 and the 9router package (~70 MB) straight into the app's files, then copies them into the guest: no apt, no npm.
2. Start. The gateway listens on `127.0.0.1:20128`; proot shares the network namespace, so the app and WebView reach it directly.
3. Open dashboard → sign in with the password shown on that screen (passed as `INITIAL_PASSWORD`).
4. Dashboard → Endpoint → API keys → create key.
5. Settings → Providers → 9Router → paste key → Connect. The preset registers `http://127.0.0.1:20128/v1` and its models.

## Notes

- Port must stay distinct from the OpenCode server port (4096/4196).
- Data lives in `/root/.9router` inside the shared rootfs. Reset environment wipes it.
- The dashboard password is generated per install and kept in app prefs; change it in the dashboard after first login if you want.
- `INSTALL` needs internet: nodejs.org for the runtime and registry.npmjs.org for the 9router package. Nothing is fetched by apt or npm inside the guest.
