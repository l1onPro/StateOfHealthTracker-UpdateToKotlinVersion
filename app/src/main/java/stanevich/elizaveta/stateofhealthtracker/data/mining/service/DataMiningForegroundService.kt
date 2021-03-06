package stanevich.elizaveta.stateofhealthtracker.data.mining.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import stanevich.elizaveta.stateofhealthtracker.R
import stanevich.elizaveta.stateofhealthtracker.data.mining.location.LocationProvider
import stanevich.elizaveta.stateofhealthtracker.data.mining.rotation.SHTNotificationManager
import stanevich.elizaveta.stateofhealthtracker.home.database.Speed
import stanevich.elizaveta.stateofhealthtracker.home.database.StatesDatabase
import java.util.*
import java.util.concurrent.TimeUnit


class DataMiningForegroundService : Service() {

    companion object {
        const val LOCATION_PERMISSIONS_KEY = "LOCATION_PERMISSIONS_KEY"
        private const val SERVICE_ENABLED = "SERVICE_ENABLED"
        private const val WHEN_SERVICE_ENABLED = "WHEN_SERVICE_ENABLED"

        const val WORK_NAME_DEFAULT = "Data Mining Foreground Service Worker"
        const val WORK_DELAY_MINUTES = 30L

        private const val COLLECTING_DATA_HOURS = 2L

        fun saveServiceEnabled(context: Context, enabled: Boolean) {
            context
                .applicationContext
                .getSharedPreferences(SERVICE_ENABLED, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SERVICE_ENABLED, enabled)
                .putLong(WHEN_SERVICE_ENABLED, Calendar.getInstance().timeInMillis)
                .apply()
        }

        fun isServiceEnabled(context: Context): Boolean {
            val sharedPreferences = context
                .applicationContext
                .getSharedPreferences(SERVICE_ENABLED, Context.MODE_PRIVATE)
            val isRunning = sharedPreferences
                .getBoolean(SERVICE_ENABLED, false)

            val whenEnabled =
                sharedPreferences.getLong(WHEN_SERVICE_ENABLED, Calendar.getInstance().timeInMillis)
            val enabledCalendar = Calendar.getInstance()
            enabledCalendar.timeInMillis = whenEnabled
            val nowCalendar = Calendar.getInstance()
            nowCalendar.add(Calendar.HOUR_OF_DAY, 2)

            return isRunning && nowCalendar.after(enabledCalendar)
        }
    }

    private lateinit var locationProvider: LocationProvider
    private var deleteNotificationPendingIntent: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()

        val speedDatabaseDao = StatesDatabase.getInstance(applicationContext).speedDatabaseDao

        locationProvider = LocationProvider(this, 1000) {
            if (it.speed > 1.5) {
                CoroutineScope(Dispatchers.IO).launch {
                    speedDatabaseDao.insert(
                        Speed(
                            null,
                            Calendar.getInstance().timeInMillis,
                            (it.speed * 3600) / 1000
                        )
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            moveToForeground()
        }
    }

    private fun createWorkRequest() = PeriodicWorkRequest
        .Builder(
            ForegroundServiceStartupWorker::class.java,
            WORK_DELAY_MINUTES,
            TimeUnit.MINUTES,
            WORK_DELAY_MINUTES / 2,
            TimeUnit.MINUTES
        )
        // setting a backoff on case the work needs to retry
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()


    private fun moveToForeground() {
        val notificationManager = SHTNotificationManager(this)
        val deletePendingIntent = deleteNotificationIntent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createMainNotificationChannel()
            val notification =
                NotificationCompat.Builder(this, notificationManager.getMainNotificationId())
                    .setSmallIcon(R.drawable.menu_ic_data_collection)
                    .setContentTitle(getString(R.string.data_mining_activated))
                    .setContentText(getString(R.string.data_mining_is_collected))
                    .setGroup("DataMiningStateOfHealthTrackerNotification")
                    .addAction(
                        android.R.drawable.ic_delete,
                        getString(R.string.data_mining_stop_collect),
                        deletePendingIntent
                    )
                    .build()
            startForeground(122451, notification)
        }
    }

    private fun deleteNotificationIntent(): PendingIntent {
        if (deleteNotificationPendingIntent == null) {
            val disableIntent = Intent(this, DataMiningForegroundService::class.java)
            disableIntent.putExtra(SERVICE_ENABLED, false)
            deleteNotificationPendingIntent = PendingIntent.getService(this, 0, disableIntent, 0)
        }
        return deleteNotificationPendingIntent!!
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.getBooleanExtra(LOCATION_PERMISSIONS_KEY, false) == true) {
            locationProvider.startTrackingLocation()
        }

        val savedState = isServiceEnabled(this)
        val enabled = intent?.getBooleanExtra(SERVICE_ENABLED, savedState) ?: savedState
        saveServiceEnabled(this, enabled)

        if (!enabled) {
            stopScheduledWorks()
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleWorks()
        return START_STICKY
    }

    private fun scheduleWorks() {
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                WORK_NAME_DEFAULT,
                ExistingPeriodicWorkPolicy.KEEP,
                createWorkRequest()
            )

        CoroutineScope(Dispatchers.Default).launch {
            delay(COLLECTING_DATA_HOURS * 60 * 60 * 1000)
            stopScheduledWorks()
            deleteNotificationIntent().send()
        }
    }

    private fun stopScheduledWorks() {
        saveServiceEnabled(applicationContext, false)
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME_DEFAULT)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveServiceEnabled(applicationContext, false)
        locationProvider.stopTrackingLocation()
    }

}
