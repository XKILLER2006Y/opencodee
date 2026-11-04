---
name: reverse-engineer-anything
description: Reverse engineering with REA (morluto/rea). Use whenever the user wants to understand how an app, feature, binary or site works without its source: native binaries, APKs and Android devices, JavaScript/Electron apps, .NET assemblies, websites, HAR/SAZ network captures, firmware, packaged apps (PyInstaller, Flutter, Unity IL2CPP), crash dumps, SQLite files. Trigger words: reverse engineer, decompile, disassemble, "how does X work inside", unpack, inspect this APK/exe/asar, trace this request.
---

# Reverse engineer anything (REA)

> Condensed version written from the public README of morluto/rea (MIT). It is not a copy of the upstream skill.
> `tools/fetch-default-skills.sh` in the app repo replaces it with the upstream files. Check `rea.tools` and the README for current commands.

REA is a local tool that exposes analysis of binaries, apps and runtime behavior through MCP and a CLI. Results carry the evidence and the limitations behind each conclusion. Your job is to drive it, read the evidence, and report honestly.

## Scope and limits (read first)

- Only lawful work: research, education, interoperability, software preservation, and software the user owns or is authorized to examine. If authorization is unclear for a third-party target, ask one short question before going deeper.
- Do not help with cracking licenses, bypassing activation or DRM, building malware, or attacking systems. Commercial tools (IDA, Hopper, JEB) need the user's own license; never suggest cracked copies or key sharing.
- Static analysis reads files and never runs the target. Runtime capture runs the target with the user's permissions: say so before doing it.
- This phone runs Ubuntu 24.04 arm64 under proot. Native providers (Hopper, Ghidra, IDA, Kuna) are normally not available here. Be upfront about that and use the static paths below, or tell the user the native step needs a computer (the app can connect to OpenCode running on one).

## What REA covers (from its README)

| Target | Needs |
|---|---|
| JavaScript / Electron apps, extracted dirs or ASAR | Node.js and npm; on Linux a procps-compatible `ps` |
| .NET assemblies | nothing (static) |
| Android APKs | headless JADX and a full JDK |
| APK signatures, Flutter apps, PyInstaller executables, Go/Rust binaries, ELF/PE metadata, GGUF/safetensors headers, IL2CPP metadata, Godot PCK | nothing (built-in static inspection) |
| Websites | a Chrome-family browser |
| HAR / SAZ captures | nothing for HAR/SAZ parsing |
| Native binaries (pseudocode, assembly, xrefs) | Hopper, Ghidra, IDA or Kuna, or caller-supplied GNU objdump / Rizin for offline inspection |
| Android devices | caller-supplied adb |
| Firmware | Binwalk / Unblob on Linux |

Requires Node.js 22.x (>=22.23.4), 24.x (>=24.22.1) or 26.12.1+ with npm.

## Workflow

1. Restate the question in one line: what behavior, in which target, what the user wants at the end (explanation, evidence, or a working re-implementation).
2. Check the environment: `node --version`, `npm --version`, and whether the `rea` command or an REA MCP tool is available. Ubuntu 24.04's packaged Node is too old; installing a current Node in the sandbox is fine when the task needs it. Say in one line what you installed.
3. Use only the official package `rea-agents` from npm (official sources: github.com/morluto/rea, rea.tools, npmjs.com/package/rea-agents). Check the exact package name; lookalikes exist. Run installs from a trusted directory outside the user's project.
4. Start from the lightest static inspection. Example from the README:

       npx -y rea-agents@latest analyze-javascript-application /absolute/path/to/app --json

   Use `rea --help` for the other commands. Do not run `rea-agents setup`: it edits the agent's MCP registration, and this app manages OpenCode's config itself.
5. Trace from the user's feature to the code: entry points, imports, IPC/network boundaries, strings, call references. Keep a short list of what you looked at.
6. Report with evidence: file or function, what it shows, and what you could not verify. Separate "observed" from "inferred". Never fill gaps with guesses presented as facts.
7. If asked to re-implement a behavior, write original code from the behavior you documented, test it against what you observed, and say what the test covers.

## Output style

Short summary first, then the evidence list, then open questions. Paths and function names in code formatting. No claims beyond the evidence.
