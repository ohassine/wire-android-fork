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

package com.wire.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.StrictMode
import androidx.work.Configuration
import co.touchlab.kermit.platformLogWriter
import com.wire.android.datastore.GlobalDataStore
import com.wire.android.di.ApplicationScope
import com.wire.android.di.KaliumCoreLogic
import com.wire.android.util.DataDogLogger
import com.wire.android.util.LogFileWriter
import com.wire.android.util.getGitBuildId
import com.wire.android.util.lifecycle.ConnectionPolicyManager
import com.wire.android.workmanager.WireWorkerFactory
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WireApplication : Application(), Configuration.Provider {

    @Inject
    @KaliumCoreLogic
    lateinit var coreLogic: Lazy<CoreLogic>

    @Inject
    lateinit var logFileWriter: Lazy<LogFileWriter>

    @Inject
    lateinit var connectionPolicyManager: Lazy<ConnectionPolicyManager>

    @Inject
    lateinit var wireWorkerFactory: Lazy<WireWorkerFactory>

    @Inject
    lateinit var globalObserversManager: Lazy<GlobalObserversManager>

    @Inject
    lateinit var globalDataStore: Lazy<GlobalDataStore>

    @Inject
    @ApplicationScope
    lateinit var globalAppScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(wireWorkerFactory.get())
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        enableStrictMode()

        globalAppScope.launch {
            initializeApplicationLoggingFrameworks()

            appLogger.i("$TAG app lifecycle")
            connectionPolicyManager.get().startObservingAppLifecycle()

            appLogger.i("$TAG api version update")
            // TODO: Can be handled in one of Sync steps
            coreLogic.get().updateApiVersionsScheduler.schedulePeriodicApiVersionUpdate()

            appLogger.i("$TAG global observers")
            globalObserversManager.get().observe()
        }
    }

    private fun enableStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
//                    .penaltyDeath() // Disabled as some devices and libraries are not compliant
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    // .penaltyDeath() TODO: add it later after fixing reported violations
                    .build()
            )
        }
    }

    private suspend fun initializeApplicationLoggingFrameworks() {
        // 1. Datadog should be initialized first
        ExternalLoggerManager.initDatadogLogger(applicationContext, globalDataStore.get())
        // 2. Initialize our internal logging framework
        val isLoggingEnabled = globalDataStore.get().isLoggingEnabled().first()
        val config = if (isLoggingEnabled) {
            KaliumLogger.Config.DEFAULT.apply {
                setLogLevel(KaliumLogLevel.VERBOSE)
                setLogWriterList(listOf(DataDogLogger, platformLogWriter()))
            }
        } else {
            KaliumLogger.Config.disabled()
        }
        // 2. Initialize our internal logging framework
        AppLogger.init(config)
        CoreLogger.init(config)
        // 3. Initialize our internal FILE logging framework
        logFileWriter.get().start()
        // 4. Everything ready, now we can log device info
        appLogger.i("Logger enabled")
        logDeviceInformation()
    }

    private fun logDeviceInformation() {
        appLogger.d(
            """
            > Device info: 
                App version=${BuildConfig.VERSION_NAME} 
                OS version=${Build.VERSION.SDK_INT}
                Phone model=${Build.BRAND}/${Build.MODEL}
                Commit hash=${applicationContext.getGitBuildId()}
        """.trimIndent()
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appLogger.w(
            "onTrimMemory called - App info: Memory trim level=${MemoryLevel.byLevel(level)}. " +
                    "See more at https://developer.android.com/reference/kotlin/android/content/ComponentCallbacks2"
        )
    }

    override fun onLowMemory() {
        super.onLowMemory()
        appLogger.w("onLowMemory called - Stopping logging, buckling the seatbelt and hoping for the best!")
        logFileWriter.get().stop()
    }

    private companion object {
        enum class MemoryLevel(val level: Int) {
            TRIM_MEMORY_BACKGROUND(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
            TRIM_MEMORY_COMPLETE(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
            TRIM_MEMORY_MODERATE(ComponentCallbacks2.TRIM_MEMORY_MODERATE),
            TRIM_MEMORY_RUNNING_CRITICAL(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
            TRIM_MEMORY_RUNNING_LOW(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW),
            TRIM_MEMORY_RUNNING_MODERATE(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE),
            TRIM_MEMORY_UI_HIDDEN(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),

            @Suppress("MagicNumber")
            TRIM_MEMORY_UNKNOWN(-1);

            companion object {
                fun byLevel(value: Int) =
                    values().firstOrNull { it.level == value } ?: TRIM_MEMORY_UNKNOWN
            }
        }

        private const val TAG = "WireApplication"
    }
}
