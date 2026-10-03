package com.sharepark

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.sharepark.platform.sync.SharedParkingSync
import com.sharepark.platform.worker.CleanupWorker
import com.sharepark.platform.worker.ReservationCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ShareParkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sharedParkingSync: SharedParkingSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleCleanupWorker()
        scheduleReservationCheck()
        sharedParkingSync.start()
    }

    private fun scheduleCleanupWorker() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cleanup_old_records",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // 15 minutes is the shortest period WorkManager allows.
    private fun scheduleReservationCheck() {
        val request = PeriodicWorkRequestBuilder<ReservationCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reservation_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
