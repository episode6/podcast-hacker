package com.episode6.podcasthacker.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.shared.R

internal actual fun PlatformContext.createDownloadScheduler(): DownloadScheduler =
    AndroidDownloadScheduler(context)

private const val JOB_ID = 6001
private const val NOTIFICATION_ID = 6002
private const val CHANNEL_ID = "downloads"

/**
 * Keeps the process alive (and network unrestricted) while the in-memory download queue
 * drains: a **user-initiated data transfer** job on api 34+ (UIDT doesn't exist earlier),
 * falling back to a dataSync foreground service on api 24–33 (WorkManager has no UIDT
 * support). Neither owns the download work — the redux side effects do — they just pin
 * the process and surface the mandatory notification.
 */
internal class AndroidDownloadScheduler(private val context: Context) : DownloadScheduler {

    override fun onQueueActive() {
        if (Build.VERSION.SDK_INT >= 34) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, DownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                // rough guess: tacita fetches each episode twice for the ad-diff
                .setEstimatedNetworkBytes(200L * 1024 * 1024, 0L)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        } else {
            val intent = Intent(context, DownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onQueueIdle() {
        if (Build.VERSION.SDK_INT >= 34) {
            DownloadJobService.finishJob(context)
        } else {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }
}

internal fun Context.downloadNotification(): Notification {
    if (Build.VERSION.SDK_INT >= 26) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    val builder = if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
    }
    return builder
        .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat_download))
        .setContentTitle("Downloading episodes")
        .setOngoing(true)
        .build()
}

/** Api 34+ user-initiated data transfer job; the queue side effect finishes it. */
class DownloadJobService : JobService() {

    private var params: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        this.params = params
        instance = this
        if (Build.VERSION.SDK_INT >= 34) {
            setNotification(
                params,
                NOTIFICATION_ID,
                downloadNotification(),
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
        return true // the redux side effects are doing the actual work
    }

    override fun onStopJob(params: JobParameters): Boolean {
        instance = null
        return false // queue state lives in memory; nothing to reschedule
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: DownloadJobService? = null

        fun finishJob(context: Context) {
            val service = instance
            if (service?.params != null) {
                service.jobFinished(service.params!!, false)
                instance = null
            } else {
                context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            }
        }
    }
}

/** Api 24–33 fallback: a dataSync foreground service pinned while the queue drains. */
class DownloadForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, downloadNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
