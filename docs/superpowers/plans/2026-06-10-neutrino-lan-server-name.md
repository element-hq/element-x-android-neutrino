# Neutrino LAN Server-Name Derivation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the embedded Neutrino homeserver advertise the device's live LAN IP as its `server_name` (so two devices on the same WiFi can federate in a demo), instead of the hardcoded `localhost:8008`.

**Architecture:** All changes are in the Android fork's `services/neutrino/impl` module. The Rust FFI already accepts `server_name` and `bind_addr` as independent fields (`NeutrinoConfig`) and binds to `bind_addr` verbatim (`neutrino-main::entrypoint`), so **no Rust / bindings changes are required**. We derive the LAN host at launch: `server_name = "<lan-ip>:8008"` (the federation identity baked into the MXID), `bind_addr = "0.0.0.0:8008"` (so peers can connect). The address-selection logic is extracted into pure, unit-testable functions; the interface enumeration sits behind a small injectable provider.

**Tech Stack:** Kotlin, Metro DI, JUnit4 + Truth (`com.google.common.truth.Truth.assertThat`), `java.net.NetworkInterface` / `InetAddress`. Gradle module `:services:neutrino:impl`.

**Scope / non-goals (demo):** This is the ephemeral demo path (Option A). The MXID becomes `@alice:<lan-ip>:8008`, so it changes whenever the device's LAN IP changes — acceptable for a throwaway demo session, **not** a persistent account. Reacting to mid-session network changes (rebinding a running server) is explicitly out of scope here and would be a follow-up (a `Command::Rebind` FFI variant + a `ConnectivityManager.NetworkCallback`). Cellular CGNAT means only same-LAN federation works; that is the intended limitation.

---

## File Structure

- **Create:** `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt`
  Pure logic: `NEUTRINO_PORT`, `NeutrinoEndpoint`, `selectLanServerHost(...)`, `serverIdentity(...)`. No Android or native dependencies — fully unit-testable.
- **Create:** `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NetworkAddressProvider.kt`
  Injectable seam over `java.net.NetworkInterface` enumeration. Interface + Metro-contributed default impl.
- **Modify:** `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/DefaultNeutrinoService.kt`
  Inject the provider, derive the endpoint at `start()`, pass it into `NeutrinoConfig`.
- **Modify:** `services/neutrino/impl/build.gradle.kts`
  Add test dependencies (`testCommonDependencies(libs)`).
- **Create:** `services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt`
  Unit tests for the two pure functions.

---

## Task 1: Test infrastructure for the impl module

The impl module currently declares no test dependencies. Add them so unit tests can compile and run.

**Files:**
- Modify: `services/neutrino/impl/build.gradle.kts`

- [ ] **Step 1: Add the test-common import and dependency**

Edit `services/neutrino/impl/build.gradle.kts`. Add the import at the top (just below the existing `import extension.setupDependencyInjection`):

```kotlin
import extension.setupDependencyInjection
import extension.testCommonDependencies
```

Then add `testCommonDependencies(libs)` to the `dependencies { }` block so it reads:

```kotlin
dependencies {
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)

    api(projects.services.neutrino.api)
    implementation(libs.neutrino)

    testCommonDependencies(libs)
}
```

- [ ] **Step 2: Verify the module still configures**

Run: `./gradlew :services:neutrino:impl:help -q`
Expected: configures with no Gradle errors (BUILD SUCCESSFUL). This confirms the new import/extension resolves before any test code exists.

- [ ] **Step 3: Commit**

```bash
git add services/neutrino/impl/build.gradle.kts
git commit -m "Add unit test dependencies to the Neutrino impl module"
```

---

## Task 2: Pure LAN host selection (`selectLanServerHost`)

Pick the address peers on the same LAN can reach this device on: the first site-local IPv4, skipping loopback, link-local, and IPv6.

**Files:**
- Create: `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt`
- Test: `services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt`

- [ ] **Step 1: Write the failing test**

Create `services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.InetAddress

class NeutrinoNetworkTest {
    @Test
    fun `selectLanServerHost prefers a site-local IPv4 over loopback`() {
        val candidates = listOf(
            InetAddress.getByName("127.0.0.1"),
            InetAddress.getByName("192.168.1.5"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("192.168.1.5")
    }

    @Test
    fun `selectLanServerHost skips link-local addresses`() {
        val candidates = listOf(
            InetAddress.getByName("169.254.1.1"),
            InetAddress.getByName("10.0.0.4"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("10.0.0.4")
    }

    @Test
    fun `selectLanServerHost skips IPv6 even when it is listed first`() {
        val candidates = listOf(
            InetAddress.getByName("2001:4860:4860::8888"),
            InetAddress.getByName("192.168.0.2"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("192.168.0.2")
    }

    @Test
    fun `selectLanServerHost returns null when only loopback is available`() {
        assertThat(selectLanServerHost(listOf(InetAddress.getByName("127.0.0.1")))).isNull()
    }

    @Test
    fun `selectLanServerHost returns null for an empty list`() {
        assertThat(selectLanServerHost(emptyList())).isNull()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :services:neutrino:impl:testDebugUnitTest --tests "io.element.android.services.neutrino.impl.NeutrinoNetworkTest"`
Expected: FAIL — compilation error, `Unresolved reference: selectLanServerHost`.

- [ ] **Step 3: Write the minimal implementation**

Create `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import java.net.Inet4Address
import java.net.InetAddress

/** Port the embedded homeserver listens on, both for the local client and federation. */
internal const val NEUTRINO_PORT = 8008

/**
 * Pick the address other devices on the same LAN can reach this device on.
 *
 * Returns the first site-local IPv4 host (e.g. "192.168.1.5"), or null when only
 * loopback / link-local / IPv6 addresses are available (e.g. offline, or behind
 * carrier NAT with no private address). IPv6 is skipped so the resulting MXID
 * stays free of `[...]` bracket escaping. When several site-local IPv4 addresses
 * exist (e.g. WiFi + VPN) the first enumerated one wins — acceptable for the demo.
 */
internal fun selectLanServerHost(candidates: List<InetAddress>): String? {
    return candidates
        .asSequence()
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress }
        .filterNot { it.isLinkLocalAddress }
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :services:neutrino:impl:testDebugUnitTest --tests "io.element.android.services.neutrino.impl.NeutrinoNetworkTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt
git commit -m "Select the device LAN IPv4 as the Neutrino server host"
```

---

## Task 3: Pure endpoint derivation (`serverIdentity`)

Turn an optional LAN host into the `server_name` + `bind_addr` pair, with a loopback fallback when there is no LAN address.

**Files:**
- Modify: `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt`
- Test: `services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt:1` (append tests to the existing class)

- [ ] **Step 1: Write the failing tests**

Add these test methods inside the existing `NeutrinoNetworkTest` class in `services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt` (before the closing brace):

```kotlin
    @Test
    fun `serverIdentity advertises the LAN host and binds all interfaces`() {
        val endpoint = serverIdentity(host = "192.168.1.5", port = 8008)
        assertThat(endpoint.serverName).isEqualTo("192.168.1.5:8008")
        assertThat(endpoint.bindAddr).isEqualTo("0.0.0.0:8008")
    }

    @Test
    fun `serverIdentity falls back to loopback when there is no LAN host`() {
        val endpoint = serverIdentity(host = null, port = 8008)
        assertThat(endpoint.serverName).isEqualTo("localhost:8008")
        assertThat(endpoint.bindAddr).isEqualTo("localhost:8008")
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :services:neutrino:impl:testDebugUnitTest --tests "io.element.android.services.neutrino.impl.NeutrinoNetworkTest"`
Expected: FAIL — compilation error, `Unresolved reference: serverIdentity` (and `NeutrinoEndpoint`).

- [ ] **Step 3: Write the minimal implementation**

Append to `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt`:

```kotlin
/**
 * The `server_name` + `bind_addr` pair for a Neutrino launch.
 *
 * - [serverName] is the federation identity baked into the user's MXID
 *   (`@localpart:serverName`). For the LAN demo it is a literal `ip:port`.
 * - [bindAddr] is the socket the server listens on.
 */
internal data class NeutrinoEndpoint(
    val serverName: String,
    val bindAddr: String,
)

/**
 * Build the federation endpoint for a launch.
 *
 * With a LAN [host] the server advertises that literal `host:port` identity and
 * binds all interfaces (`0.0.0.0`) so peers can connect. With no LAN address it
 * falls back to loopback, so the local client still works offline (no peer can
 * reach it, but the device talks to its own server over loopback regardless).
 */
internal fun serverIdentity(host: String?, port: Int = NEUTRINO_PORT): NeutrinoEndpoint {
    return if (host == null) {
        NeutrinoEndpoint(serverName = "localhost:$port", bindAddr = "localhost:$port")
    } else {
        NeutrinoEndpoint(serverName = "$host:$port", bindAddr = "0.0.0.0:$port")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :services:neutrino:impl:testDebugUnitTest --tests "io.element.android.services.neutrino.impl.NeutrinoNetworkTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetwork.kt services/neutrino/impl/src/test/kotlin/io/element/android/services/neutrino/impl/NeutrinoNetworkTest.kt
git commit -m "Derive Neutrino server name and bind address from the LAN host"
```

---

## Task 4: Injectable network address provider

Wrap `NetworkInterface` enumeration behind a small interface so `DefaultNeutrinoService` stays decoupled from the platform call. This glue is environment-dependent and is not unit-tested; it is verified by compilation and the manual run in Task 6.

**Files:**
- Create: `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NetworkAddressProvider.kt`

- [ ] **Step 1: Write the implementation**

Create `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NetworkAddressProvider.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Source of the device's current network addresses. Abstracted so the launch
 * logic in [DefaultNeutrinoService] can be exercised with fake candidates.
 */
interface NetworkAddressProvider {
    fun currentAddresses(): List<InetAddress>
}

/**
 * Enumerates the live, non-loopback network interfaces. Requires no permission.
 * Any [java.net.SocketException] from a transient interface query collapses to an
 * empty list, which [serverIdentity] treats as "offline" (loopback fallback).
 */
@ContributesBinding(AppScope::class, binding = binding<NetworkAddressProvider>())
class NetworkInterfaceAddressProvider : NetworkAddressProvider {
    override fun currentAddresses(): List<InetAddress> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
        }.getOrDefault(emptyList())
    }
}
```

- [ ] **Step 2: Verify it compiles (and DI graph is valid)**

Run: `./gradlew :services:neutrino:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. This confirms the Metro `@ContributesBinding` annotation and imports resolve.

- [ ] **Step 3: Commit**

```bash
git add services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/NetworkAddressProvider.kt
git commit -m "Add a network address provider over the device interfaces"
```

---

## Task 5: Wire the derived endpoint into `DefaultNeutrinoService`

Inject the provider, derive the endpoint at launch, and pass it to `NeutrinoConfig`.

**Files:**
- Modify: `services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/DefaultNeutrinoService.kt`

- [ ] **Step 1: Add the constructor dependency**

Edit the class declaration in `DefaultNeutrinoService.kt` to inject the provider:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NeutrinoService>())
class DefaultNeutrinoService(
    @ApplicationContext private val context: Context,
    private val networkAddressProvider: NetworkAddressProvider,
) : NeutrinoService {
```

- [ ] **Step 2: Derive the endpoint and use it in `start()`**

Replace the body of `start()` so the hardcoded `localhost:8008` becomes the derived endpoint:

```kotlin
    override fun start() {
        if (handle != null) {
            return
        }
        val host = selectLanServerHost(networkAddressProvider.currentAddresses())
        val endpoint = serverIdentity(host)
        Timber.i("Starting embedded Neutrino server as %s (bind %s)", endpoint.serverName, endpoint.bindAddr)
        try {
            handle = io.element.neutrino.start(io.element.neutrino.NeutrinoConfig(
                serverName = endpoint.serverName,
                bindAddr = endpoint.bindAddr,
                localpart = "alice",
                storageDir = context.filesDir.resolve("data").path,
                outboundConcurrency = 4u,
            ))
        } catch (t: Throwable) {
            Timber.e(t, "Neutrino failed to start")
        }
    }
```

(The `serverName`/`bindAddr` are Matrix IDs, not PII — safe to log per AGENTS.md.)

- [ ] **Step 3: Verify the module compiles and all unit tests still pass**

Run: `./gradlew :services:neutrino:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 7 tests pass, no compilation errors.

- [ ] **Step 4: Build the debug app to confirm the DI graph resolves end-to-end**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Confirms `NetworkAddressProvider` is satisfied in `AppScope` where `DefaultNeutrinoService` is constructed.

- [ ] **Step 5: Commit**

```bash
git add services/neutrino/impl/src/main/kotlin/io/element/android/services/neutrino/impl/DefaultNeutrinoService.kt
git commit -m "Launch Neutrino with the device LAN address as its server name"
```

---

## Task 6: Manual verification (federation across two devices)

The native boundary and real federation cannot be unit-tested. Verify on hardware/emulators on the same WiFi.

- [ ] **Step 1: Install the debug build on two devices on the same LAN**

Run: `./gradlew :app:installDebug` (with each device/emulator selected, or `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`).

- [ ] **Step 2: Confirm each device advertises its LAN IP**

Run: `adb -s <serial> logcat -s Neutrino:* | grep "Starting embedded Neutrino server"`
Expected: a line like `Starting embedded Neutrino server as 192.168.1.5:8008 (bind 0.0.0.0:8008)` — the host is the device's actual WiFi IP, not `localhost`. (If it shows `localhost:8008`, the device has no site-local IPv4 — check it is on WiFi, not cellular-only.)

- [ ] **Step 3: Confirm the port is reachable from the peer**

From device B (or a machine on the same LAN), Run: `adb shell` then `curl -s http://<device-A-ip>:8008/_matrix/federation/v1/version` (or hit it from a laptop on the same WiFi).
Expected: an HTTP response from device A's Neutrino (not connection-refused). Connection-refused means it is still bound to loopback — re-check `bind_addr` is `0.0.0.0:8008`.

- [ ] **Step 4: Exercise a federated join**

On device A create a room; from device B (`@alice:<device-B-ip>:8008`) join it via its room id. Verify the membership and a message round-trips between the two devices.
Expected: device B joins and both see each other's messages — federation between the two embedded servers works.

- [ ] **Step 5: No commit**

This task is verification only; nothing to commit.

---

## Notes for the executor

- **Module path:** `:services:neutrino:impl`. Unit-test task is `testDebugUnitTest`; `--tests "FQCN"` filters to one class.
- **Commit style:** sentence-style messages, no conventional-commit prefixes (per `AGENTS.md`).
- **No Rust changes:** `NeutrinoConfig` already separates `server_name`/`bind_addr` (`crates/neutrino-ffi/src/lib.rs:11`) and `entrypoint` binds to `bind_addr` verbatim (`crates/neutrino-main/src/lib.rs:8`). Do not rebuild or republish the `io.element.neutrino:bindings` artifact.
- **Local client unaffected:** the device's own Matrix client keeps talking to the server over loopback (`http://localhost:8008`); the literal-IP `server_name` only changes the federation identity / MXID, which Matrix clients tolerate (homeserver URL ≠ server name).
- **Known demo limitations (by design):** MXID changes if the LAN IP changes; mid-session network changes are not handled; carrier-NAT / cellular-only devices fall back to loopback and cannot federate.
```
