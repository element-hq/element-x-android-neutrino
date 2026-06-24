/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package extension

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Properties
import javax.inject.Inject

/**
 * The working directory the git command runs in. Must be set explicitly: an injected
 * [ExecOperations] otherwise inherits the process working directory, which for the Gradle
 * daemon is a daemon-internal dir, not the repo, making git fail with "not a git repository".
 */
interface GitValueSourceParameters : ValueSourceParameters {
    val workingDir: DirectoryProperty
}

abstract class GitRevisionValueSource : ValueSource<String, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        return execOperations.runCommand("git rev-parse --short=8 HEAD", parameters.workingDir.get().asFile)
    }
}

abstract class GitBranchNameValueSource : ValueSource<String, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        return execOperations.runCommand("git rev-parse --abbrev-ref HEAD", parameters.workingDir.get().asFile)
    }
}

/**
 * Number of commits reachable from HEAD, used as a monotonic build number in the version name.
 * Note: a shallow clone (e.g. CI with limited fetch depth) reports a truncated count.
 */
abstract class GitCommitCountValueSource : ValueSource<String, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        return execOperations.runCommand("git rev-list --count HEAD", parameters.workingDir.get().asFile)
    }
}

private fun ExecOperations.runCommand(cmd: String, dir: File): String {
    val outputStream = ByteArrayOutputStream()
    val errorStream = ByteArrayOutputStream()
    exec {
        commandLine = cmd.split(" ")
        workingDir = dir
        standardOutput = outputStream
        errorOutput = errorStream
    }
    if (errorStream.size() > 0) {
        println("Error while running command: $cmd")
        throw IOException(String(errorStream.toByteArray()))
    }
    return String(outputStream.toByteArray()).trim()
}

fun Project.readLocalProperty(name: String): String? = Properties().apply {
    try {
        load(rootProject.file("local.properties").reader())
    } catch (ignored: IOException) {
    }
}.getProperty(name)
