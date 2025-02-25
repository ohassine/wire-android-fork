/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package scripts

import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.ProductFlavor
import customization.ConfigType
import customization.Customization.getBuildtimeConfiguration
import customization.FeatureConfigs
import customization.FeatureFlags
import customization.Features
import customization.overrideResourcesForAllFlavors
import flavor.FlavorDimensions
import flavor.ProductFlavors

plugins { id("com.android.application") apply false }
// DO NOT USE CAPITAL LETTER FOR THE BUILD TYPE NAME OR JENKINS WILL BE MAD
object BuildTypes {
    const val DEBUG = "debug"
    const val RELEASE = "release"
    const val COMPAT = "compat"
    const val COMPAT_RELEASE = "compatrelease"
    const val BENCHMARK = "benchmark"
}

object Default {
    fun explicitBuildFlavor(): String? = System.getenv("flavor")
        ?: System.getenv("FLAVOR")

    fun resolvedBuildFlavor(): String = explicitBuildFlavor() ?: ProductFlavors.Dev.buildName

    fun explicitBuildType(): String? = System.getenv("buildType")
        ?: System.getenv("BUILD_TYPE")

    fun resolvedBuildType(): String = explicitBuildType() ?: BuildTypes.DEBUG

    val BUILD_VARIANT = "${resolvedBuildFlavor().capitalize()}${resolvedBuildType().capitalize()}"
}

fun NamedDomainObjectContainer<ApplicationProductFlavor>.createAppFlavour(
    flavorApplicationId: String,
    sharedUserId: String,
    flavour: ProductFlavors
) {
    create(flavour.buildName) {
        dimension = flavour.dimensions
        applicationId = flavorApplicationId
        versionNameSuffix = "-${flavour.buildName}"
        resValue("string", "app_name", flavour.appName)
        manifestPlaceholders["sharedUserId"] = sharedUserId
        manifestPlaceholders["appAuthRedirectScheme"] = flavorApplicationId
    }
}


android {
    val enableSigning = System.getenv("ENABLE_SIGNING").equals("TRUE", true)
    if (enableSigning) {
        signingConfigs {
            maybeCreate(BuildTypes.RELEASE).apply {
                val keystorePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                storeFile = file("$keystorePath/keystore.jks")
                storePassword = System.getenv("KEYSTOREPWD_RELEASE")
                keyAlias = System.getenv("KEYSTORE_KEY_NAME_RELEASE")
                keyPassword = System.getenv("KEYPWD_RELEASE")
            }
            maybeCreate(BuildTypes.DEBUG).apply {
                val keystorePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                storeFile = file("$keystorePath/keystore.jks")
                storePassword = System.getenv("KEYSTOREPWD_RELEASE")
                keyAlias = System.getenv("KEYSTORE_KEY_NAME_RELEASE")
                keyPassword = System.getenv("KEYPWD_RELEASE")
            }
            maybeCreate(BuildTypes.COMPAT).apply {
                val keystorePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                storeFile = file("$keystorePath/keystore.jks")
                storePassword = System.getenv("KEYSTOREPWD_RELEASE")
                keyAlias = System.getenv("KEYSTORE_KEY_NAME_RELEASE")
                keyPassword = System.getenv("KEYPWD_RELEASE")
            }
            maybeCreate(BuildTypes.COMPAT_RELEASE).apply {
                val keystorePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                storeFile = file("$keystorePath/keystore.jks")
                storePassword = System.getenv("KEYSTOREPWD_RELEASE")
                keyAlias = System.getenv("KEYSTORE_KEY_NAME_RELEASE")
                keyPassword = System.getenv("KEYPWD_RELEASE")
            }
            maybeCreate(BuildTypes.BENCHMARK).apply {
                val keystorePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                storeFile = file("$keystorePath/keystore.jks")
                storePassword = System.getenv("KEYSTOREPWD_RELEASE")
                keyAlias = System.getenv("KEYSTORE_KEY_NAME_RELEASE")
                keyPassword = System.getenv("KEYPWD_RELEASE")
            }
        }
    }

    buildTypes {
        getByName(BuildTypes.DEBUG) {
            isMinifyEnabled = false
            applicationIdSuffix = ".${BuildTypes.DEBUG}"
            isDebuggable = true
            // Just in case a developer is trying to debug some prod crashes by turning on minify
            if (isMinifyEnabled) proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (enableSigning)
                signingConfig = signingConfigs.getByName("debug")
        }
        getByName(BuildTypes.RELEASE) {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            if (enableSigning)
                signingConfig = signingConfigs.getByName("release")
        }
        create(BuildTypes.COMPAT) {
            initWith(getByName(BuildTypes.RELEASE))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            matchingFallbacks.add("release")
            if (enableSigning)
                signingConfig = signingConfigs.getByName("compat")
        }
        create(BuildTypes.COMPAT_RELEASE) {
            initWith(getByName(BuildTypes.RELEASE))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            matchingFallbacks.add("release")
            if (enableSigning)
                signingConfig = signingConfigs.getByName("compatrelease")
        }
        create(BuildTypes.BENCHMARK) {
            initWith(getByName(BuildTypes.RELEASE))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            matchingFallbacks.add("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions(FlavorDimensions.DEFAULT)

    val buildtimeConfiguration = getBuildtimeConfiguration(rootDir = rootDir)
    val flavorMap = buildtimeConfiguration.flavorSettings.flavorMap

    productFlavors {
        fun createFlavor(flavor: ProductFlavors) {
            val flavorName = flavor.buildName
            val flavorSpecificMap = flavorMap[flavorName]
            requireNotNull(flavorSpecificMap) {
                "Missing configs in json file for the flavor '$flavorName'"
            }
            val flavorApplicationId = flavorSpecificMap[FeatureConfigs.APPLICATION_ID.value] as? String
            requireNotNull(flavorApplicationId) {
                "Missing application ID definition for the flavor '$flavorName'"
            }
            // prefer value from FeatureConfigs if defined, otherwise fallback to in-code flavor value.
            val userId: String = (flavorSpecificMap[FeatureConfigs.USER_ID.value] as? String) ?: flavor.shareduserId
            createAppFlavour(
                flavorApplicationId = flavorApplicationId,
                sharedUserId = userId,
                flavour = flavor
            )
        }
        ProductFlavors.all.forEach(::createFlavor)
    }

    buildtimeConfiguration.customResourceOverrideDirectory?.let {
        overrideResourcesForAllFlavors(it)
    }

    /**
     * Process feature flags and if the feature is not included in a product flavor,
     * a default value of "false" or "deactivated" is used.
     *
     * @see "FeatureFlags.kt" file definition.
     */
    productFlavors.forEach { flavor ->
        Features.values().forEach { feature ->
            val activated = FeatureFlags.activated.mapKeys { it.key.buildName }[flavor.name].orEmpty().contains(feature)
            flavor.buildConfigField("Boolean", feature.name, activated.toString())
        }

        FeatureConfigs.values().forEach { configs ->
            when (configs.configType) {
                ConfigType.STRING -> {
                    buildStringConfig(
                        flavor,
                        configs.configType.type,
                        configs.name,
                        flavorMap[flavor.name]?.get(configs.value)?.toString()
                    )
                }

                ConfigType.INT,
                ConfigType.BOOLEAN -> {
                    buildNonStringConfig(
                        flavor,
                        configs.configType.type,
                        configs.name,
                        flavorMap[flavor.name]?.get(configs.value).toString()
                    )
                }

                ConfigType.MapOfStringToListOfStrings -> {
                    val map = flavorMap[flavor.name]?.get(configs.value) as? Map<*, *>
                    val mapString = map?.map { (key, value) ->
                        "\"$key\", java.util.Arrays.asList(${(value as? List<*>)?.joinToString { "\"$it\"" } ?: ""})".let {
                            "put($it);"
                        }
                    }?.joinToString(",\n") ?: ""
                    buildNonStringConfig(
                        flavor,
                        configs.configType.type,
                        configs.name,
                        "new java.util.HashMap<String, java.util.List<String>>() {{\n$mapString\n}}"
                    )
                }
            }
        }
    }
}

fun buildStringConfig(productFlavour: ProductFlavor, type: String, name: String, value: String?) {
    productFlavour.buildConfigField(
        type,
        name,
        value?.let { """"$it"""" } ?: "null"
    )
}

fun buildNonStringConfig(productFlavour: ProductFlavor, type: String, name: String, value: String) {
    productFlavour.buildConfigField(
        type,
        name,
        value
    )
}
