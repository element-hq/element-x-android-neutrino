/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl.tunnel

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber
import java.io.FileInputStream
import java.io.IOException

/**
 * A [VpnService] that opens a TUN interface, restricts it to *this application's*
 * traffic, and logs every IP packet the OS routes into the TUN.
 *
 * Nothing is forwarded yet: this is the capture-and-log spike that precedes wiring
 * up a transport (BLE) and the embedded Neutrino homeserver. Only traffic this app
 * sends to the virtual TUN subnet ([TUN_SUBNET_IPV4] / [TUN_SUBNET_IPV6]) is routed
 * into the TUN (and dropped); every other destination bypasses it, so the app keeps
 * normal connectivity.
 */
class NeutrinoTunnelService : VpnService() {
    companion object {
        // Whether the tunnel is currently established. Process-global (the VPN cannot
        // outlive the process), so it is a safe source of truth for the UI to read
        // back on screen (re)entry. Set true once the interface is up, false on teardown.
        @Volatile
        var isActive: Boolean = false
            private set

        private const val NOTIFICATION_CHANNEL_ID = "neutrino_tunnel_channel"

        // Must be non-zero for startForeground.
        private const val NOTIFICATION_ID = 0x4E54 // "NT"

        // Virtual TUN addressing. Only this virtual subnet is routed into the TUN,
        // so all other destinations bypass it and the app keeps normal connectivity.
        // Nothing is forwarded yet; traffic to the subnet is captured and logged.
        private const val TUN_IPV4 = "10.0.0.2"
        private const val TUN_IPV4_PREFIX = 24
        private const val TUN_SUBNET_IPV4 = "10.0.0.0"
        private const val TUN_IPV6 = "fd00::2"
        private const val TUN_IPV6_PREFIX = 64
        private const val TUN_SUBNET_IPV6 = "fd00::"
        private const val TUN_MTU = 1280

        // Generous upper bound for a single read; TUN packets are MTU-bounded in
        // practice, but the kernel may hand up larger frames (e.g. GRO), so size
        // the buffer well above the MTU to avoid truncating a read.
        private const val READ_BUFFER_SIZE = 32_767

        private const val ACTION_STOP = "io.element.android.services.neutrino.impl.tunnel.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NeutrinoTunnelService::class.java))
        }

        fun stop(context: Context) {
            // Deliver an explicit stop command so the running instance closes its TUN
            // fd itself. The system binds to an established VpnService, so a plain
            // stopService() does not reach onDestroy until the fd is closed — so close
            // it from onStartCommand, which is reliably invoked.
            ContextCompat.startForegroundService(
                context,
                Intent(context, NeutrinoTunnelService::class.java).apply { action = ACTION_STOP },
            )
        }
    }

    @Volatile
    private var running = false
    private var tunInterface: ParcelFileDescriptor? = null
    private var readThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Timber.i("Neutrino tunnel: stop requested")
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        establishTunnel()
        return START_NOT_STICKY
    }

    private fun establishTunnel() {
        if (tunInterface != null) {
            return
        }
        val builder = Builder()
            .setSession("Neutrino Tunnel")
            .addAddress(TUN_IPV4, TUN_IPV4_PREFIX)
            .addAddress(TUN_IPV6, TUN_IPV6_PREFIX)
            .addRoute(TUN_SUBNET_IPV4, TUN_IPV4_PREFIX)
            .addRoute(TUN_SUBNET_IPV6, TUN_IPV6_PREFIX)
            .setMtu(TUN_MTU)
        // Restrict the tunnel to ONLY this application's traffic; every other app
        // bypasses the TUN. Our own process hosts the embedded Neutrino homeserver,
        // so this captures exactly the traffic we care about and nothing else.
        runCatchingExceptions {
            builder.addAllowedApplication(packageName)
        }.onFailure {
            Timber.e(it, "Neutrino tunnel: failed to restrict to own application")
        }
        val pfd = builder.establish()
        if (pfd == null) {
            Timber.e("Neutrino tunnel: establish() returned null (consent not granted?)")
            stopSelf()
            return
        }
        tunInterface = pfd
        isActive = true
        Timber.i("Neutrino tunnel established: fd=${pfd.fd}, mtu=$TUN_MTU, app=$packageName")
        startReadLoop(pfd)
    }

    private fun startReadLoop(pfd: ParcelFileDescriptor) {
        running = true
        readThread = Thread({ readLoop(pfd) }, "neutrino-tun-read").apply { start() }
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        // Reading the fd yields the IP packets this app sends to the virtual subnet.
        FileInputStream(pfd.fileDescriptor).use { input ->
            try {
                while (running) {
                    val length = input.read(buffer)
                    if (length < 0) {
                        break // EOF: fd closed.
                    }
                    if (length > 0) {
                        Timber.d("Neutrino tunnel tx: ${IpPacket.describe(buffer, length)}")
                    }
                }
            } catch (e: IOException) {
                // Closing the fd in onDestroy unblocks read() with an IOException.
                if (running) {
                    Timber.w(e, "Neutrino tunnel: read loop ended unexpectedly")
                }
            }
        }
    }

    override fun onRevoke() {
        Timber.i("Neutrino tunnel: consent revoked, stopping")
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    // Idempotent: invoked from the explicit stop command and again from onDestroy.
    private fun teardown() {
        isActive = false
        running = false
        // Closing the descriptor tears down the VPN interface (removing the system VPN)
        // and unblocks the read loop; the thread then exits.
        runCatchingExceptions { tunInterface?.close() }
        readThread?.interrupt()
        tunInterface = null
        readThread = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startForeground() {
        val notificationManager = NotificationManagerCompat.from(this)
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName("Neutrino tunnel").build()
        notificationManager.createNotificationChannel(channel)

        // Dev-only placeholder strings: the tunnel is not user-facing yet, so these
        // are intentionally not localised via temporary.xml.
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Neutrino tunnel")
            .setContentText("Capturing this app's packets")
            .setOngoing(true)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatchingExceptions {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        }.onFailure {
            Timber.e(it, "Neutrino tunnel: failed to start foreground service")
        }
    }
}
