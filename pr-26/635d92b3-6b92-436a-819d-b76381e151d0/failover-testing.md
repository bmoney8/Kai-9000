# Failover Test Matrix

**Last verified:** 2026-11-25 (static source review only)

The offline-first failover change adds three `commonTest` suites. This document records what each
one covers and how CI should run them. The authoring environment for this change has no JDK by
design, so no Gradle/JVM execution happened here; CI must run the commands below.

## Commands

```bash
CI=true ./gradlew :composeApp:jvmTest --tests 'com.inspiredandroid.kai.data.*Failover*' --console=plain --no-daemon
CI=true ./gradlew test --console=plain --no-daemon
```

## Added coverage

### `EndpointHealthProbeTest`

- Successful `/models` fetch returns reachable.
- Failed fetch (connection error) returns unreachable.
- A slow fetch is converted to unreachable at the configured timeout.
- A real `CancellationException` is rethrown, not reported as an outage.

### `FailoverPreflightTest`

- Unreachable OpenAI-compatible primary is skipped for one turn and LiteRT answers.
- Local image capability changes the route from `text` to `text + image`.
- Reachable primary leaves the configured chain untouched.
- Next turn re-probes the original chain (reversible failover).
- Disabled setting skips the probe entirely.
- Non-OpenAI-compatible primary and chains without an on-device target are never probed.
- Tooltip precedence: a preflight routing decision replaces the generic fallback status text.

### `FailoverSettingsTest`

- Defaults: failover enabled, timeout 1500 ms.
- `service_openai-compatible_failover_enabled` and
  `service_openai-compatible_failover_timeout_ms` round-trip through `AppSettings`.
- Non-positive timeout values are coerced to 1 ms.

## Baseline preserved

`FallbackTurnTraversal`, its commonTest suite, the LiteRT inference loop, and the chat request /
response DTOs were not modified by this change.
