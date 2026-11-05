# Fixes in this revision

Signing / CI
- Release APK is signed with `keystore/release.jks` (committed, private repo); CI no longer needs `SIGNING_KEYSTORE_B64`.
- CI: unit tests, tag-vs-versionName check, signature verification against the keystore, `.sha256` asset, concurrency guard,
  optional publishing to a separate public releases repo (`RELEASE_REPO` / `RELEASE_TOKEN`).
- Updater / bug report / "Source on GitHub" use `updateRepo` from `gradle.properties` (`BuildConfig.UPDATE_REPO`) instead of a hard-coded repo.

Security
- Updater verifies the APK against GitHub's `digest` (sha256) when present.
- OpenCode update refuses to install without a checksum, and restarts the server if the install fails.
- PhoneBridge: request body capped (4 MB) and token checked before the body is read (any local app could crash the app with a huge Content-Length);
  constant-time token compare; preview files are streamed; path check no longer accepts sibling folders with the same prefix.
- TarExtractor: "inside rootfs" check uses a path separator (`rootfs-evil` no longer passes), hard links outside the rootfs are skipped, header records are size-capped.
- Skills: `delete("..")` could wipe the parent folder; zip import is now capped at 64 MB unpacked.
- Backup: the temp prefs file (server password, phone token) and the temp archive are always removed, also when export/restore fails.

Other bugs
- Downloader reused any cached file when no checksum was given (stale APK after a re-upload) and never checked the downloaded length.
- EventHub: multi-line SSE `data:` fields are joined with a newline as the spec says.

# Default skills
- `app/src/main/assets/default-skills/` holds `anti-ai-slop-writing` and `reverse-engineer-anything`. `DefaultSkills.seed()` copies each into the skills folder once
  (re-seeded after a fresh Ubuntu install; a skill the user deletes stays deleted).
- `DefaultSkills.prompt` adds a short always-on block to the managed part of AGENTS.md (via `Memory.sync`) so the agent applies them without being asked.
  The block only lists skills that are still installed. Cost: about 300 tokens of context per chat.
- The bundled SKILL.md files are condensed from the two READMEs (the upstream SKILL.md pages could not be fetched when this was written).
  `tools/fetch-default-skills.sh` swaps in the upstream files.

# Kamihub provider
- `data/CustomProviders.kt` (preset `KAMIHUB`): checks the key against `GET /v1/models`, writes a `@ai-sdk/openai-compatible` provider block
  (baseURL + models) to the global config through `FreeModels.apply`, and stores the key with `PUT /auth/kamihub`.
- `ProvidersScreen` has a Kamihub row under "Add provider" and a key sheet. Disconnect uses the normal provider disconnect.
- A refused key (HTTP 401/403) stops before anything is written; a gateway without `/models` falls back to `alicn/deepseek-v4.1-flash` only.

# Release numbering
- The workflow runs on every push to main (docs-only pushes are ignored) and takes the version from the run number
  (`-PappVersionCode`, `-PappVersionName` into `app/build.gradle.kts`). No tag or input to type; the tag is created at the built commit.

# Rename to CeloAgents
- App name (`app_name`, share-sheet labels), icon labels, look name, mascot tab, onboarding, assistant persona, splash letters and `tools/connect-qr.sh` all say CeloAgents.
- (superseded by the package rename below)
- Connection links use `celoagents://connect?...`; the old `cl0udly://` links are still accepted. The release APK is named `CeloAgents-<tag>.apk`.

- Package / applicationId changed to `com.celoext` (was `app.ocmobile`): it installs as a new app, it does not update the old one.

# OpenCode 2.x provider connection and config fixes
- Provider integration methods report API-key authentication as `type: "key"`, not `"api"`. The provider sheet now routes `key` methods to `/api/integration/{id}/connect/key`; previously it treated them as OAuth, which explains why Google OAuth could work while API-key providers failed during Connect.
- The API-key fallback method now uses the same `key` type, while `api` remains accepted for older server responses.
- Custom OpenAI-compatible providers, Copilot and Pollinations now use the OpenCode 2.x `providers` config namespace, `package: "@opencode/ai/providers/openai-compatible"`, and `settings` instead of the retired V1 `npm/options` shape. Model capabilities use the V2 schema.
- Runtime port probing now parses the stored string port as an integer before constructing `InetSocketAddress`, fixing the Kotlin compile failure.
