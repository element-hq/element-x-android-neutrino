/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.MediaStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.neutrino.api.CaptureResult
import io.element.android.services.neutrino.api.DiscoveredPeer
import io.element.android.services.neutrino.api.NetworkAddressProvider
import io.element.android.services.neutrino.api.NeutrinoService
import io.element.neutrino.CaptureException
import io.element.neutrino.NeutrinoHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val READINESS_POLL_INTERVAL_MS = 100L
private const val READINESS_CONNECT_TIMEOUT_MS = 500

// Capture filenames are timestamped (neutrino-YYYYMMDD-HHMMSS.pcap) rather than
// stable: MediaStore rows created by a previous install can't be replaced (an app
// may only delete rows it owns, and ownership is lost on reinstall), so a stable
// name silently collides and gets auto-renamed to "name (1).pcap" — leaving the
// old file at the stable path, where `adb pull` fetches it as if it were fresh.
private fun newCaptureFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "neutrino-$stamp.pcap"
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NeutrinoService>())
class DefaultNeutrinoService(
    @ApplicationContext private val context: Context,
    private val networkAddressProvider: NetworkAddressProvider,
) : NeutrinoService {
    var handle: NeutrinoHandle? = null

    // Held so its NetworkCallback is not garbage-collected. Registered once, on
    // first successful start; lives for the app-singleton's process lifetime.
    private var connectivityKicker: ConnectivityKicker? = null

    override fun start() {
        if (handle != null) {
            return
        }
        val host = selectLanServerHost(networkAddressProvider.currentAddresses())
        val bindAddr = selectBindAddr(host)
        Timber.i("Starting embedded Neutrino server (bind $bindAddr)")
        // Bring the BLE backend up before starting the server: the server binds its
        // iroh-over-BLE federation transport during start, so blew must be
        // initialised first. The caller (the startup splash) has already gated this
        // on the BLE runtime permissions being granted.
        initBleNativeOnce()
        try {
            handle = io.element.neutrino.ble.startBle(io.element.neutrino.NeutrinoConfig(
                // server_name is no longer supplied: the homeserver derives it from
                // its node identity and reports it back via handle.serverName().
                bindAddr = bindAddr,
                // The single forced user. The login flow auto-logs-in as this localpart
                // (see LoginFlowNode's forced-provider path).
                localpart = "n",
                storageDir = context.filesDir.resolve("data").path,
                outboundConcurrency = 4u,
                // Run the in-process low-bandwidth (CoAP/UDP) federation sidecar on
                // the federation port; null would mean direct federation instead.
                lbFederationPort = NEUTRINO_FEDERATION_PORT.toUShort(),
                // Add signatures to events
                trustedNetwork = false,
                // derive the server name from the random secret
                serverName = null,
            ))
        } catch (t: Throwable) {
            Timber.e(t, "Neutrino failed to start")
            return
        }
        Timber.i("Neutrino server started as ${handle?.serverName()}")
        // The server is up. Reset its outbound federation backoff whenever the
        // device regains connectivity, so a returning-online device reconnects
        // promptly instead of waiting out a long backoff.
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        connectivityKicker = ConnectivityKicker(connectivityManager) {
            Timber.i("Connectivity regained; sending KickBackoff to Neutrino")
            handle?.kickBackoff()
        }.apply { register() }
    }

    override suspend fun awaitReady(timeoutMs: Long) {
        if (handle == null) return
        val ready = withTimeoutOrNull(timeoutMs) {
            while (!withContext(Dispatchers.IO) { isCsPortOpen() }) {
                // A fatal startup failure is published asynchronously after start()
                // returns (e.g. a server_name mismatch against existing data); the
                // CS listener will then never bind, so stop waiting immediately and
                // let the caller surface lastError() instead of blocking the full
                // timeout on a server that is never coming up.
                if (handle?.lastError() != null) return@withTimeoutOrNull false
                delay(READINESS_POLL_INTERVAL_MS)
            }
            true
        } ?: false
        if (ready) {
            Timber.i("Neutrino client-server API is accepting connections")
        } else {
            Timber.w("Neutrino client-server API not reachable after ${timeoutMs}ms")
        }
    }

    // The listener binds asynchronously after `start()` returns; probe the CS port
    // with a short-timeout TCP connect ("connection refused" until it's bound).
    private fun isCsPortOpen(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("localhost", NEUTRINO_PORT), READINESS_CONNECT_TIMEOUT_MS)
        }
        true
    } catch (t: Throwable) {
        // Expected while the listener is still binding (e.g. connection refused).
        Timber.v(t, "Neutrino CS port not open yet")
        false
    }

    override fun isRunning(): Boolean {
        return handle != null
    }

    override fun serverName(): String? = handle?.serverName()

    override fun lastError(): String? = handle?.lastError()

    override fun discoveredPeers(): List<DiscoveredPeer> =
        handle?.discoveredPeers()?.map { peer ->
            DiscoveredPeer(
                serverName = peer.serverName,
                displayName = peer.displayName,
                lastSeenMs = peer.lastSeenMs.toLong(),
            )
        }.orEmpty()

    // The path of the in-flight capture, so stopCapture can report where it was
    // written. Null when no capture is running.
    private var captureFilePath: String? = null

    override fun startCapture(): CaptureResult {
        val handle = handle ?: return CaptureResult.Failed("Neutrino is not running")
        // The live capture is written to app-specific external storage (needs no
        // permission and takes a real filesystem path, which the native handle
        // requires). On stop it's copied into the public Downloads folder — see
        // stopCapture — so the developer never has to type the long private path.
        val dir = context.getExternalFilesDir(null)
            ?: return CaptureResult.Failed("External storage is unavailable")
        val file = File(dir, newCaptureFileName())
        return try {
            handle.startCapture(file.path)
            captureFilePath = file.path
            Timber.i("Neutrino federation capture started: ${file.path}")
            CaptureResult.Started(file.path)
        } catch (e: CaptureException) {
            Timber.e(e, "Neutrino federation capture failed to start")
            val reason = (e as? CaptureException.Io)?.reason ?: e.message ?: "unknown error"
            CaptureResult.Failed(reason)
        }
    }

    override fun stopCapture(): String? {
        val wasRunning = handle?.stopCapture() == true
        val sourcePath = captureFilePath
        captureFilePath = null
        if (!wasRunning || sourcePath == null) return null
        // The native handle has flushed + closed the file (stopCapture joins its
        // writer), so it's safe to copy the finished pcap into Downloads.
        val source = File(sourcePath)
        val downloads = exportToDownloads(source)
        return if (downloads != null) {
            source.delete()
            Timber.i("Neutrino federation capture saved to Downloads: $downloads")
            downloads
        } else {
            Timber.i("Neutrino federation capture stopped: $sourcePath")
            sourcePath
        }
    }

    override fun isCapturing(): Boolean = handle?.isCapturing() == true

    // Copy the finished pcap into the public Downloads collection so it lands at a
    // short path (`/sdcard/Download/neutrino-<timestamp>.pcap`) that's trivial to
    // `adb pull` and visible in the Files app — far better dev UX than the
    // app-private external path. Returns the user-facing "Download/<name>" location
    // — read back from MediaStore, since an insert that collides with an existing
    // row is silently renamed ("name (1).pcap") rather than overwriting — or null
    // (keeping the app-private file) on older devices or error.
    // MediaStore Downloads is API 29+; needs no storage permission.
    private fun exportToDownloads(source: File): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, source.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.tcpdump.pcap")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: return null
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            // The name MediaStore actually stored the file under, which is not
            // necessarily the requested one (collision → "name (1).pcap").
            val actualName = resolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: source.name
            "Download/$actualName"
        } catch (t: Throwable) {
            Timber.e(t, "Failed to export capture to Downloads")
            resolver.delete(uri, null, null)
            null
        }
    }

    // Bootstrap blew's Android backend once, replicating what its Tauri
    // `BlewPlugin.load()` does (we don't use the Tauri plugin):
    //  1. NativeBle.initialise — registers the JavaVM + app Context with native
    //     `ndk_context` and runs `init_jvm` (caches the manager classes).
    //  2. BleCentralManager/BlePeripheralManager.init(context) — hands the app
    //     Context to the Kotlin managers, which their static
    //     `areBlePermissionsGranted()` reads; without this the permission check
    //     runs against a null context and fails even when perms are granted.
    // Failures are logged, not fatal — the server still runs (federation just has
    // no BLE path).
    private var bleNativeInitialised = false

    private fun initBleNativeOnce() {
        if (bleNativeInitialised) return
        try {
            val appContext = context.applicationContext
            io.element.neutrino.NativeBle.initialise(appContext)
            org.jakebot.blew.BleCentralManager.init(appContext)
            org.jakebot.blew.BlePeripheralManager.init(appContext)
            bleNativeInitialised = true
        } catch (t: Throwable) {
            Timber.e(t, "BLE native init failed")
        }
    }
}
