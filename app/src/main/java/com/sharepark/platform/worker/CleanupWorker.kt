package com.sharepark.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharepark.domain.usecase.CleanupOldRecordsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cleanupOldRecordsUseCase: CleanupOldRecordsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val deletedCount = cleanupOldRecordsUseCase()
            if (deletedCount > 0) {
                // Log/trace deletion count
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
