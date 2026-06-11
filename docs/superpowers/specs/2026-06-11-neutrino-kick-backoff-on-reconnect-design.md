# Fire Neutrino `kickBackoff()` on connectivity regain

**Date:** 2026-06-11
**Repo:** `element-x-android-neutrino`
**Scope:** `services/neutrino/impl` only — no public API change, no upstream Element X edits. (One `uses-permission` manifest is added within this module; see Permission.)

## Goal

The embedded Neutrino homeserver backs off its outbound federation retries
(exponential, jittered, capped) when a destination is unreachable. When the
device goes offline and later regains connectivity, those destinations would
otherwise wait out a potentially long backoff before reconnecting. Neutrino's
FFI now exposes `NeutrinoHandle.kickBackoff()`, which resets every outbound
destination's backoff to base and retries immediately.

This change wires the Android host to call `kickBackoff()` whenever the device
**regains** network connectivity, so a returning-online device reconnects its
federation promptly instead of idling in backoff.

## Constraints

- **Self-contained in `services/neutrino/impl`.** Keep the fork's diff against
  upstream Element X minimal: no edits to `appnav`, `app`, `features/*`, or
  shared libraries; no change to the `NeutrinoService` API. (A one-line
  `uses-permission` manifest is added inside this module — still within the
  fork's own service module, not an upstream edit; see Permission.)
- **No reuse of `NetworkMonitor`.** `NetworkMonitor` lives in
  `features/networkmonitor` and its `api` pulls in `designsystem`/`uiStrings`.
  A `service` depending on a `feature` would invert the module layering, so
  Neutrino owns its own connectivity listener instead.

## Design

### Components

1. **`ReconnectDetector`** (internal, pure — no Android types)
   - A tiny state machine that turns raw availability transitions into "should
     we kick now?" decisions.
   - API: `fun onAvailable(): Boolean` (returns `true` iff this is a genuine
     regain), `fun onLost()`.
   - State: a single `private var wasOffline = false`, initialised `false` so
     the **initial** `onAvailable` (fired at registration when already online)
     does **not** kick.
   - Semantics: `onLost()` sets `wasOffline = true`; `onAvailable()` returns
     `true` and clears the flag only if `wasOffline` was set. A network handover
     that produces `onAvailable` without a preceding `onLost` returns `false`.
   - This is the only logic with branching, and it is unit-testable without
     Robolectric.

2. **`ConnectivityKicker`** (internal — thin Android plumbing)
   - Constructed with a `ConnectivityManager` and an `onReconnect: () -> Unit`.
   - Owns one `ConnectivityManager.NetworkCallback` that delegates its
     `onAvailable`/`onLost` to a `ReconnectDetector`, invoking `onReconnect()`
     when the detector reports a regain.
   - `register()` — idempotent; calls `registerDefaultNetworkCallback(callback)`
     once (guarded by an internal `registered` flag).
   - `unregister()` — calls `unregisterNetworkCallback(callback)`; provided for
     correctness/testability even though no caller exists yet (see Lifecycle).
   - Uses `registerDefaultNetworkCallback` (API 24+, within both FOSS minSdk 24
     and enterprise minSdk 33) — we care about "does the device have a default
     network", not per-transport detail.

3. **`DefaultNeutrinoService`** (existing — minimal edit)
   - After a successful `io.element.neutrino.start(...)` in `start()`, construct
     a `ConnectivityKicker` from `context.getSystemService(ConnectivityManager)`
     with `onReconnect = { Timber.i(...); handle?.kickBackoff() }`, and call
     `register()`. The single Timber line (no PII) records each kick send; the
     Neutrino-specific log lives here rather than in the generic kicker.
   - Hold the kicker in a field so it (and its callback) is not GC'd.
   - The existing `if (handle != null) return` guard at the top of `start()`
     keeps registration to exactly once.

### Data flow

```
ConnectivityManager (default network)
   → NetworkCallback.onLost()      → ReconnectDetector.onLost()      (mark offline)
   → NetworkCallback.onAvailable() → ReconnectDetector.onAvailable() → true on regain
        → onReconnect()  ==  handle?.kickBackoff()  (FFI: reset backoff, retry now)
```

### Threading

`NetworkCallback` methods run on a framework binder/handler thread.
`kickBackoff()` is a non-blocking FFI call that only enqueues a command on an
unbounded channel inside Neutrino, so it is safe to invoke directly from the
callback thread. No coroutine, dispatcher, or app `CoroutineScope` is needed.

### Lifecycle

`DefaultNeutrinoService` is `@SingleIn(AppScope::class)` and has no `stop()`,
so the embedded server runs for the whole app process lifetime. The kicker's
callback therefore also lives for the process lifetime and is reclaimed on
process death — mirroring how the app's own `NetworkMonitor` never unregisters
its singleton callback. `unregister()` exists so the class is self-consistent
and testable, and so a future `NeutrinoService.stop()` can call it; it is not
wired to anything in this change.

### Permission

`ConnectivityManager` callbacks require `ACCESS_NETWORK_STATE`. Although
`libraries/androidutils` declares it (so the final app APK has it at runtime),
dependency permissions merge only at the **application** manifest level — not
into a library module's own merged manifest. Lint's `MissingPermission` check
runs per-module, so `registerDefaultNetworkCallback` in `services/neutrino/impl`
needs the permission declared in *this* module's manifest. This mirrors
`features/networkmonitor/impl`, which declares the same permission for the same
reason. A minimal `services/neutrino/impl/src/main/AndroidManifest.xml` adds it.

## Testing

- **`ReconnectDetectorTest`** (plain JVM unit test, no Robolectric):
  - initial `onAvailable()` (already online at registration) returns `false`;
  - `onLost()` then `onAvailable()` returns `true` (genuine regain);
  - `onAvailable()` with no preceding `onLost()` (handover) returns `false`;
  - repeated `onLost()`/`onAvailable()` cycles each produce exactly one kick;
  - back-to-back `onLost()` calls then one `onAvailable()` kicks once.
- The `ConnectivityKicker` ↔ `ConnectivityManager` registration is thin Android
  plumbing; it is exercised by manual/device verification rather than a unit
  test (the framework `ConnectivityManager`/`Network` types are awkward to fake
  without Robolectric, and the branching logic lives in `ReconnectDetector`).

## Non-goals

- No debounce of connectivity flapping. A redundant kick is harmless: it only
  resets backoff to base, and a still-unreachable destination simply backs off
  again. (Can be added later if device testing shows churn.)
- No reuse of `NetworkMonitor` (see Constraints).
- No `NeutrinoService.stop()` / teardown path (out of scope; tracked by the
  unused-but-present `unregister()`).
- No change to when/where `NeutrinoService.start()` is invoked.

## Files

- **New:** `services/neutrino/impl/.../impl/ConnectivityKicker.kt`
  (contains `ConnectivityKicker` and the internal `ReconnectDetector`).
- **New (test):** `services/neutrino/impl/src/test/.../impl/ReconnectDetectorTest.kt`.
- **New:** `services/neutrino/impl/src/main/AndroidManifest.xml`
  (declares `ACCESS_NETWORK_STATE`; see Permission).
- **Edit:** `services/neutrino/impl/.../impl/DefaultNeutrinoService.kt`
  (construct + `register()` the kicker in `start()`, hold it in a field).

## Verification

> **Environment note:** these checks need the Android/JDK build toolchain. They
> cannot run in a sandbox without a JDK/Android SDK, so they are run on a
> machine that has the toolchain.

- `./gradlew :services:neutrino:impl:test`
- `./gradlew assembleDebug` (or `:services:neutrino:impl:assembleDebug`)
- `./gradlew ktlintFormat` / `lint`
- Manual device check: start the server, toggle airplane mode off→on→off, and
  confirm a `kickBackoff()`-driven federation retry follows the regain (a Timber
  log on the kick path can confirm).
