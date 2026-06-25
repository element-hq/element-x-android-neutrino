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
import android.system.Os
import android.system.OsConstants
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.services.neutrino.api.NeutrinoService
import timber.log.Timber

/**
 * A [VpnService] that opens a TUN interface, restricts it to *this application's*
 * traffic, and hands the TUN file descriptor to the embedded Neutrino homeserver,
 * which reads and logs every IP packet the OS routes into the TUN.
 *
 * Nothing is forwarded yet: this is the capture-and-log spike that precedes wiring
 * up a transport (BLE) and the embedded Neutrino homeserver. Only traffic this app
 * sends to the virtual TUN subnet ([TUN_SUBNET_IPV4] / [TUN_SUBNET_IPV6]) is routed
 * into the TUN (and dropped); every other destination bypasses it, so the app keeps
 * normal connectivity.
 *
 * The packet read loop lives in native code (the `neutrino` library): we transfer
 * ownership of the fd to it via [NeutrinoService.attachTunnel] and it owns the fd
 * from then on, closing it (and so tearing down the TUN interface) on
 * [NeutrinoService.detachTunnel] or when the homeserver stops.
 */
class NeutrinoTunnelService : VpnService() {
    @Inject
    lateinit var neutrinoService: NeutrinoService

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

    // Guards against re-establishing on a redundant onStartCommand within this
    // instance. Ownership of the fd is transferred to native code, so the service
    // does not retain the ParcelFileDescriptor.
    @Volatile
    private var tunnelStarted = false

    override fun onCreate() {
        super.onCreate()
        bindings<NeutrinoTunnelServiceBindings>().inject(this)
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
        if (tunnelStarted) {
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
        // The native reader registers the fd with the tokio reactor, which requires
        // it to be non-blocking. Set O_NONBLOCK while we still own the descriptor.
        val nonBlocking = runCatchingExceptions {
            val flags = Os.fcntlInt(pfd.fileDescriptor, OsConstants.F_GETFL, 0)
            Os.fcntlInt(pfd.fileDescriptor, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
        }.onFailure {
            Timber.e(it, "Neutrino tunnel: failed to set O_NONBLOCK; not starting tunnel")
        }.isSuccess
        if (!nonBlocking) {
            pfd.close()
            stopSelf()
            return
        }
        // Transfer ownership of the fd to native code: it reads + logs packets and
        // closes the fd on teardown. After detachFd the ParcelFileDescriptor is spent,
        // so we do not retain or close it here.
        val fd = pfd.detachFd()
        tunnelStarted = true
        isActive = true
        Timber.i("Neutrino tunnel established: fd=$fd, mtu=$TUN_MTU, app=$packageName")
        neutrinoService.attachTunnel(fd, TUN_MTU)
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
        tunnelStarted = false
        // Native code owns the fd: detaching it aborts the reader, which closes the
        // fd and so tears down the VPN interface (removing the system VPN). Idempotent
        // on the native side when nothing is attached.
        neutrinoService.detachTunnel()
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
