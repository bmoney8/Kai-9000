# Vision

A commercial, white-label AI assistant built on a clean fork of `SimonSchubert/Kai` (Kotlin Multiplatform / Jetpack Compose, Apache-2.0). The application delivers practical, everyday AI workflows—interactive UI screens, persistent memory, scheduled heartbeats, and MCP tool integrations—packaged with hybrid local-first inference so customers never hit cloud subscription caps or require technical setup.

## Goals
- Deliver reproducible, white-labeled native installers for Windows (`.msi`), macOS (`.dmg`), and Linux (`.deb`/`.AppImage`) without Electron or Docker dependencies.
- Pre-wire a zero-config hybrid routing chain: Priority 1 routes to a custom OpenAI-compatible gateway (**Omniroute**), and Priority 2 fails over automatically to native **on-device LiteRT inference** (e.g., Gemma 4 E2B/E4B).
- Ship a turnkey customer provisioning flow using Kai's JSON settings schema so builds launch pre-configured with default routing, curated Streamable HTTP MCP servers, and customizable system prompts ("Soul").
- Replace developer-focused clutter (such as gaming integrations) with a simplified **Customer Mode** while keeping advanced settings accessible behind an **Admin Mode** toggle.
- Maintain clean upstream git hygiene so bug fixes and LiteRT updates from `SimonSchubert/Kai` can be cherry-picked with minimal merge friction.

## Target users
- **Non-technical business and everyday desktop/mobile users** running standard consumer hardware (**16GB system RAM, CPU-only / no dedicated GPU**).
- Customers who want an out-of-the-box AI assistant that works reliably offline or on-device, avoids complex API key management, and eliminates monthly usage cap anxiety.

## Principles
- **Respect the 16GB / no-GPU ceiling:** Keep baseline client idle RAM under 200MB before model load, never introduce Electron, Chromium sidecars, or Docker runtime requirements, and guard local model selection against out-of-memory conditions.
- **Small, modular changes:** Keep custom branding, Omniroute preset loaders, and Customer Mode UI toggles decoupled from core upstream engine files to preserve easy upstream syncing.
- **Zero-friction defaults:** Every customer build must work immediately on first launch with zero terminal commands or manual JSON editing required by the end user.
- **Strict license compliance:** Preserve all upstream Apache-2.0 copyright notices, attribution, and third-party license disclosures across all rebranded builds.

## Out of scope
- Adding Electron, Node.js runtimes, Docker containers, or heavy server-side dependencies to the client package.
- Proposing large local models (>4B parameters or unquantized weights) that exceed a 16GB CPU-only RAM budget.
- Building custom cloud billing, subscription paywalls, or proprietary telemetry trackers into the client.
- Large-scale refactors of upstream Kotlin Multiplatform architecture or Compose UI primitives unless required for rebranding or stability.
- Retaining non-business consumer integrations (e.g., Splinterlands auto-battle) in customer-facing builds.

## Quality bar
- **Resource & failover verification:** Every release candidate must pass automated checks confirming <200MB idle memory footprint and seamless fallback from Omniroute to on-device LiteRT when network connectivity drops.
- **Test coverage:** Every behavior change, preset loader, or UI mode toggle includes unit or Compose screenshot/UI tests (`./gradlew test` / `:screenshotTests`).
- **Green CI before merge:** All Gradle builds, linters, license checks, and platform packaging tasks must pass cleanly before merging.

## Protected areas
Paths (globs in backticks) and topics that always need a human. The factory never merges
changes that touch them; they go to your inbox.
- `**/auth/**`
- `**/security/**`
- `**/encryption/**`
- `**/migrations/**`
- `.github/workflows/**`
- `fastlane/**`
- `LICENSE*`
- `THIRD_PARTY_LICENSES.md`
- `SECURITY.md`
- payments
- licensing
- code signing certificates and release keys
