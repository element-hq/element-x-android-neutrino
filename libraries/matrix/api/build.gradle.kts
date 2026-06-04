import config.BuildTimeConfig
import extension.buildConfigFieldNullableStr
import extension.buildConfigFieldStr
import extension.testCommonDependencies

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.matrix.api"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigFieldStr(
            name = "CLIENT_URI",
            value = BuildTimeConfig.URL_WEBSITE ?: "https://github.com/element-hq/element-x-android-neutrino"
        )
        buildConfigFieldNullableStr(
            name = "LOGO_URI",
            value = BuildTimeConfig.URL_LOGO
        )
        buildConfigFieldNullableStr(
            name = "TOS_URI",
            value = BuildTimeConfig.URL_ACCEPTABLE_USE
        )
        buildConfigFieldNullableStr(
            name = "POLICY_URI",
            value = BuildTimeConfig.URL_POLICY
        )
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.services.analytics.api)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
}
