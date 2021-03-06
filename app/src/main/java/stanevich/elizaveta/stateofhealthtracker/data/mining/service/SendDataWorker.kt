package stanevich.elizaveta.stateofhealthtracker.data.mining.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import stanevich.elizaveta.stateofhealthtracker.home.database.StatesDatabase
import stanevich.elizaveta.stateofhealthtracker.network.api.Result.Status
import stanevich.elizaveta.stateofhealthtracker.network.api.dataStore.DataStoreAPI
import stanevich.elizaveta.stateofhealthtracker.network.api.dataStore.NetworkDao
import stanevich.elizaveta.stateofhealthtracker.network.dto.convertToSendWrapper
import stanevich.elizaveta.stateofhealthtracker.test.games.database.TestingDatabase

class SendDataWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        withContext(Dispatchers.IO) {
            val dataStoreApi = DataStoreAPI()

            try {
                TestingDatabase.getInstance(applicationContext).getAllNetworkDao().forEach {
                    commonSendData(dataStoreApi, it)
                }
                StatesDatabase.getInstance(applicationContext).getAllNetworkDao().forEach {
                    commonSendData(dataStoreApi, it)
                }

                Result.Success()
            } catch (e: Exception) {
                Log.e("SendingError", e.toString())
                Result.failure()
            }
        }
    }

    private suspend fun commonSendData(dataStoreApi: DataStoreAPI, database: NetworkDao<Any>) {
        val data = database.findAll().map { convertToSendWrapper(it) }
        if (data.isNotEmpty()) {
            if (dataStoreApi.isAlive().status === Status.SUCCESS) {
                val res = dataStoreApi.sendByDto(data)
                if (res?.status === Status.SUCCESS) {
                    database.clear()
                }
            }
        }
    }
}