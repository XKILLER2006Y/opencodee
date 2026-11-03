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

