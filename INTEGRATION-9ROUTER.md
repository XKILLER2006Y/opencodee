# 9Router integration

Adds a second local server to the app: the [9Router](https://github.com/decolua/9router) AI gateway runs inside the same Ubuntu proot environment as `opencode serve`, on its own port (default 20128), with its own foreground service.

## What was added

### Runtime
- `runtime/NineRouterController.kt` — installs Node.js (apt) and the `9router` npm package inside the guest rootfs, runs `9router --port 20128 --host 127.0.0.1 --no-browser --skip-update --log`, supervises it (health poll on `/api/health`, watchdog, restart with backoff), tracks its log, and generates/keeps the dashboard password and JWT secret in `Prefs`.
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

1. Settings → Local server → 9Router server → Install. (`apt-get install nodejs npm`, then `npm install -g 9router` in the guest.)
2. Start. The gateway listens on `127.0.0.1:20128`; proot shares the network namespace, so the app and WebView reach it directly.
3. Open dashboard → sign in with the password shown on that screen (passed as `INITIAL_PASSWORD`).
4. Dashboard → Endpoint → API keys → create key.
5. Settings → Providers → 9Router → paste key → Connect. The preset registers `http://127.0.0.1:20128/v1` and its models.

## Notes

- Port must stay distinct from the OpenCode server port (4096/4196).
- Data lives in `/root/.9router` inside the shared rootfs. Reset environment wipes it.
- The dashboard password is generated per install and kept in app prefs; change it in the dashboard after first login if you want.
- `INSTALL` needs internet inside proot (apt + npm registry).
