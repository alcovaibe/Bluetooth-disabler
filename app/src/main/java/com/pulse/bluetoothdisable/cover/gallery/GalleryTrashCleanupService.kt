package com.pulse.bluetoothdisable.cover.gallery

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Persistent local cleanup; no exact-alarm permission, network request or media-library access. */
class GalleryTrashCleanupService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cleanup: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        cleanup = scope.launch {
            val result = runCatching {
                val repository = GalleryRepository(applicationContext)
                repository.purgeExpiredTrash()
                repository.nextTrashExpiry()
            }
            withContext(Dispatchers.Main) {
                jobFinished(params, result.isFailure)
                if (result.isSuccess) schedule(applicationContext, result.getOrNull())
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cleanup?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x47414C

        internal fun schedule(context: Context, expiresAt: Long?) {
            // Scheduling must never turn a committed import into a rollback that deletes its files.
            runCatching {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return@runCatching
                if (expiresAt == null) {
                    scheduler.cancel(JOB_ID)
                    return@runCatching
                }
                val latency = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
                val job = JobInfo.Builder(JOB_ID, ComponentName(context, GalleryTrashCleanupService::class.java))
                    .setMinimumLatency(latency)
                    .setOverrideDeadline(latency + 60 * 60 * 1000)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(job)
            }.onFailure { android.util.Log.w("GalleryTrashCleanup", "Cleanup will retry when Gallery is opened", it) }
        }

        internal fun initialize(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val repository = GalleryRepository(context.applicationContext)
                    repository.purgeExpiredTrash()
                    schedule(context.applicationContext, repository.nextTrashExpiry())
                }
            }
        }
    }
}
