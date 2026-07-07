/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.api

/**
 * Outcome of arming a federation pcap capture (see [NeutrinoService.startCapture]).
 */
sealed interface CaptureResult {
    /** Capture is running, writing to [filePath] (an absolute, adb-pullable path). */
    data class Started(val filePath: String) : CaptureResult

    /** Capture could not be started; [reason] is a human-readable explanation. */
    data class Failed(val reason: String) : CaptureResult
}
