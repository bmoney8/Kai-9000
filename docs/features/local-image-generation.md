# Local Image Generation (on-device seam)

**Status:** additive provider contract and benchmark plan. No native runtime is bundled yet.

Kai's LiteRT-LM text inference remains the only active local engine. This page documents the separate **local image-provider seam** that a future `stable-diffusion.cpp` adapter plugs into, without changing the text inference loop, the Windows build scripts, or adding a cloud service.

## What is in this change

- `LocalImageGenerationEngine` with `suspend generate(request)` and synchronous, idempotent `unload()`.
- `generateImageAndUnload(...)` wrapper that calls `unload()` in a `finally` block after every successful **and** failed image request.
- A small model whitelist for 512 px-class, 1–4 step, quantized models.
- Platform factories (`createLocalImageGenerationEngine()`) that return `null` until a native backend is compiled and bundled. Every platform source set has an `actual`, so no target is missing a declaration.
- Unit tests for validation, backend resolution/fallback, benchmark-only model exclusion, and unload-on-success/failure.

## What is deliberately *not* in this change

- No `stable-diffusion.cpp` source, JNI bridge, native binary, Vulkan/AVX2/DirectML SDK, or model file is added.
- No new Gradle dependency and no change to Windows packaging/MSI scripts.
- No image-generation UI is wired into chat or Settings. `Service.supportsImages` today only describes *sending image attachments to vision models*; it does not declare image generation.
- No install footprint, latency, or peak-RAM number in this document is a measurement. This environment has no JDK, GPU, or native toolchain, so nothing was built or benchmarked.

## Reference hardware and budgets

| Item | Target |
|------|--------|
| Device | i5 10th-gen, 4–6 cores, AVX2, integrated UHD/Iris iGPU, 16 GB RAM, 256 GB SSD |
| Resolution | 512×512, offline, 1–4 steps |
| RAM headroom | OS + LiteRT-LM ≥ 6 GB free immediately after generation (hard requirement) |
| Latency | < 10 s per 512×512 image is a **stretch target to validate**, not a pass/fail gate |
| Discrete GPU | Never required or detected; AVX2 CPU must stand alone |

## Backend policy

| Backend | Role |
|---------|------|
| `VULKAN_IGPU` | Preferred only when the integrated UHD/Iris Vulkan driver advertises support |
| `AVX2_CPU` | Guaranteed CPU fallback; the reference-tier stand-alone path |
| `DIRECTML` | Optional Windows/ML path, capability-gated; never required |

`resolveImageBackend(preferred, available, unavailable)` never invents a capability: if the preferred backend is not in `available` (or was marked `unavailable` after a failure), it selects the next candidate from the whitelist and falls back to AVX2. If no candidate is available, it returns `null` and the caller fails explicitly rather than loading an unsafe native library. No discrete-GPU backend is defined in the enum.

## Model whitelist

Model files are user-managed local files. The app does not download, stream, or fetch them from a URL, and validation rejects paths containing a URI scheme.

| Model id | Family | Quant | Steps | Shipping? |
|----------|--------|-------|-------|-----------|
| `sdxs-512-q4` | SDXS-512 | Q4 | 1–4 | yes |
| `sdxl-lightning-q4` | SDXL-Lightning | Q4 | 1–4 | yes |
| `step-distilled-sd-q4` | step-distilled Stable Diffusion | Q4 | 1–4 | yes |
| `flux-2-klein-4b-q4-k-m-benchmark` | FLUX.2-klein-4B | Q4_K_M | 1–4 | **benchmark-only**; rejected by request validation |

## Memory and storage contract

- The engine must not keep a loaded model, context, or native buffer alive after `unload()` returns. The wrapper guarantees this on both success and exception paths.
- Generated images are held in memory only for the duration of one request; no image cache is introduced.
- Model storage is a dedicated local directory, separate from LiteRT-LM's `.litertlm` imports. Eviction policy for a future bundled adapter: delete a model file only after its owning generation request has finished, on explicit user request, or when the app is asked to free space; never delete the active LiteRT-LM text model to make room for an image model.
- A native adapter must be measured for peak resident memory on the reference machine before it can be enabled. The ≥6 GB headroom requirement is enforced by the adapter's measurement report, not assumed.

## Benchmark matrix (plan — no measurements yet)

Reference: i5 10th-gen (4–6 cores, AVX2), Intel UHD/Iris iGPU, 16 GB RAM, 512×512, 1–4 steps. Wall-clock is measured from `generate()` entry to result; peak RAM is sampled for the whole process during generation; both are recorded after `unload()` completes.

| Model | Backend | Steps | Wall-clock / image | Process peak RAM | ≥6 GB headroom after unload |
|-------|---------|-------|--------------------|------------------|-----------------------------|
| SDXS-512 Q4 | VULKAN_IGPU | 1 | not measured | not measured | not measured |
| SDXS-512 Q4 | VULKAN_IGPU | 4 | not measured | not measured | not measured |
| SDXS-512 Q4 | AVX2_CPU | 1 | not measured | not measured | not measured |
| SDXS-512 Q4 | AVX2_CPU | 4 | not measured | not measured | not measured |
| SDXL-Lightning Q4 | VULKAN_IGPU | 1 | not measured | not measured | not measured |
| SDXL-Lightning Q4 | AVX2_CPU | 1 | not measured | not measured | not measured |
| Step-distilled SD Q4 | VULKAN_IGPU | 1 / 4 | not measured | not measured | not measured |
| Step-distilled SD Q4 | AVX2_CPU | 1 / 4 | not measured | not measured | not measured |
| FLUX.2-klein-4B Q4_K_M (stretch only) | VULKAN_IGPU | 1–4 | not measured | not measured | not measured |
| FLUX.2-klein-4B Q4_K_M (stretch only) | AVX2_CPU | 1–4 | not measured | not measured | not measured |
| *Optional* DirectML | DIRECTML | 1 / 4 | not measured | not measured | not measured |

`<10 s` is a stretch target only. Every cell above is a placeholder to fill on the reference device once a native adapter exists; no result has been fabricated.

## Install-footprint ledger (plan — no measurements yet)

| Component | Storage | Disposition |
|-----------|---------|-------------|
| `stable-diffusion.cpp` native library (future) | TBD — measure the shipped `.dll`/`.so` on Windows | Not added in this change |
| Vulkan loader/driver (future) | Already supplied by the OS/driver on reference hardware; verify, do not bundle | Not added in this change |
| DirectML runtime (future, optional) | TBD — measure if the optional backend is bundled | Not added in this change |
| SDXS-512 Q4 model file | TBD — measure the exact pinned file | User-managed local file; not bundled |
| SDXL-Lightning Q4 model file | TBD — measure the exact pinned file | User-managed local file; not bundled |
| Step-distilled SD Q4 model file | TBD — measure the exact pinned file | User-managed local file; not bundled |
| FLUX.2-klein-4B Q4_K_M | TBD — benchmark-only | Not shipped |

The ledger must be filled with exact byte counts when a model is pinned and a native library is bundled, and the total checked against the 256 GB SSD budget. Third-party license disclosures for any bundled native runtime are a protected-area change requiring human review.

## Verification status

Static source review only: no JDK/Gradle execution is available in this environment, so the authored tests were **not run**, no binary was built, and no benchmark or footprint number was measured. Repository-scoped contract checks can be reproduced without a JDK via `scripts/verify-local-image-seam.py`. Build verification, the full test suite, and the benchmark matrix must be completed in an environment with the JDK, native toolchain, and reference hardware before this provider can be enabled.
