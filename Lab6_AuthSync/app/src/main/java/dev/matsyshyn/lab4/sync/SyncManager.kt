package dev.matsyshyn.lab4.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dev.matsyshyn.lab4.auth.AuthManager
import dev.matsyshyn.lab4.db.AppDatabase
import dev.matsyshyn.lab4.db.StabilityRecord
import dev.matsyshyn.lab4.utils.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs

class SyncManager private constructor(context: Context) {
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val authManager: AuthManager = AuthManager.getInstance(context)
    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private val context: Context = context.applicationContext
    
    private var realtimeListener: ListenerRegistration? = null
    
    companion object {
        private const val TAG = "SyncManager"
        private const val COLLECTION_RECORDS = "stability_records"
        private const val DUPLICATE_TIME_WINDOW = 1000L
        
        @Volatile
        private var INSTANCE: SyncManager? = null
        
        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    suspend fun uploadUnsyncedRecords(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val userId = authManager.getCurrentUserId()
            if (userId == null) {
                return@withContext SyncResult(false, "User not authenticated", 0, 0)
            }
            
            val unsyncedRecords = database.stabilityDao().getUnsyncedRecords()
            
            if (unsyncedRecords.isEmpty()) {
                Log.d(TAG, "No unsynced records to upload")
                return@withContext SyncResult(true, "No records to upload", 0, 0)
            }
            
            var successCount = 0
            var errorCount = 0
            
            for (record in unsyncedRecords) {
                try {
                    val cloudId = uploadRecord(record, userId)
                    if (cloudId != null) {
                        val updatedRecord = record.copy(
                            syncedToCloud = true,
                            cloudId = cloudId,
                            syncedAt = System.currentTimeMillis()
                        )
                        database.stabilityDao().update(updatedRecord)
                        successCount++
                    } else {
                        errorCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading record ${record.id}: ${e.message}")
                    errorCount++
                }
            }
            
            Log.d(TAG, "Upload complete: $successCount success, $errorCount errors")
            SyncResult(
                success = errorCount == 0,
                message = "Uploaded $successCount records",
                successCount = successCount,
                errorCount = errorCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            SyncResult(false, "Upload failed: ${e.message}", 0, 1)
        }
    }
    
    private suspend fun uploadRecord(record: StabilityRecord, userId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Перевірка чи запис вже існує в Firebase
            val existingQuery = firestore.collection(COLLECTION_RECORDS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("timestamp", record.timestamp)
                .whereEqualTo("deviceId", record.deviceId)
                .limit(1)
                .get()
                .await()
            
            if (!existingQuery.isEmpty) {
                // Запис вже існує - повертаємо існуючий cloudId
                val existingDoc = existingQuery.documents.first()
                Log.d(TAG, "Record already exists in cloud: ${existingDoc.id}")
                return@withContext existingDoc.id
            }
            
            val data = hashMapOf(
                "timestamp" to record.timestamp,
                "stabilityScore" to record.stabilityScore.toDouble(),
                "x" to record.x.toDouble(),
                "y" to record.y.toDouble(),
                "z" to record.z.toDouble(),
                "userId" to userId,
                "deviceId" to record.deviceId,
                "deviceName" to record.deviceName,
                "deviceModel" to record.deviceModel,
                "manufacturer" to record.manufacturer,
                "osVersion" to record.osVersion,
                "osSdkInt" to (record.osSdkInt?.toLong() ?: 0L),
                "isDuplicate" to record.isDuplicate,
                "duplicateGroupId" to record.duplicateGroupId,
                "syncedAt" to System.currentTimeMillis()
            )
            
            val docRef = firestore.collection(COLLECTION_RECORDS).document()
            docRef.set(data).await()
            
            Log.d(TAG, "Uploaded record to cloud: ${docRef.id}")
            return@withContext docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading record: ${e.message}", e)
            return@withContext null
        }
    }
    
    suspend fun downloadRecords(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val userId = authManager.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "Cannot download: user not authenticated")
                return@withContext SyncResult(false, "User not authenticated", 0, 0)
            }
            
            val querySnapshot = firestore.collection(COLLECTION_RECORDS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            var successCount = 0
            var errorCount = 0
            
            val existingRecords = database.stabilityDao().getAllRecords().first()
            val existingCloudIds = existingRecords.mapNotNull { it.cloudId }.toSet()
            
            for (document in querySnapshot.documents) {
                try {
                    val data = document.data
                    if (data == null) continue
                    
                    // Перевірка чи запис вже існує локально
                    if (existingCloudIds.contains(document.id)) {
                        continue
                    }
                    
                    val cloudRecord = mapToRecord(document.id, data)
                    val isDuplicate = checkForDuplicates(cloudRecord)
                    
                    if (isDuplicate) {
                        val duplicateRecord = cloudRecord.copy(
                            isDuplicate = true,
                            duplicateGroupId = generateDuplicateGroupId(cloudRecord.timestamp)
                        )
                        database.stabilityDao().insert(duplicateRecord)
                    } else {
                        database.stabilityDao().insert(cloudRecord)
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing document ${document.id}: ${e.message}")
                    errorCount++
                }
            }
            
            Log.d(TAG, "Download complete: $successCount success, $errorCount errors")
            SyncResult(
                success = errorCount == 0,
                message = "Downloaded $successCount records",
                successCount = successCount,
                errorCount = errorCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            SyncResult(false, "Download failed: ${e.message}", 0, 1)
        }
    }
    
    private fun mapToRecord(cloudId: String, data: Map<String, Any?>): StabilityRecord {
        return StabilityRecord(
            id = 0,
            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
            stabilityScore = (data["stabilityScore"] as? Double)?.toFloat() ?: 0f,
            x = (data["x"] as? Double)?.toFloat() ?: 0f,
            y = (data["y"] as? Double)?.toFloat() ?: 0f,
            z = (data["z"] as? Double)?.toFloat() ?: 0f,
            userId = data["userId"] as? String,
            deviceId = data["deviceId"] as? String,
            deviceName = data["deviceName"] as? String,
            deviceModel = data["deviceModel"] as? String,
            manufacturer = data["manufacturer"] as? String,
            osVersion = data["osVersion"] as? String,
            osSdkInt = (data["osSdkInt"] as? Long)?.toInt(),
            syncedToCloud = true,
            cloudId = cloudId,
            syncedAt = data["syncedAt"] as? Long,
            isDuplicate = (data["isDuplicate"] as? Boolean) ?: false,
            duplicateGroupId = data["duplicateGroupId"] as? String
        )
    }
    
    private suspend fun checkForDuplicates(record: StabilityRecord): Boolean {
        val existing = database.stabilityDao().getAllRecords().first()
        return existing.any { existing ->
            existing.deviceId == record.deviceId &&
            existing.userId == record.userId &&
            abs(existing.timestamp - record.timestamp) <= DUPLICATE_TIME_WINDOW
        }
    }
    
    private fun generateDuplicateGroupId(timestamp: Long): String {
        return "dup_${timestamp}_${UUID.randomUUID().toString().take(8)}"
    }
    
    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val uploadResult = uploadUnsyncedRecords()
        val downloadResult = downloadRecords()
        
        SyncResult(
            success = uploadResult.success && downloadResult.success,
            message = "Upload: ${uploadResult.message}, Download: ${downloadResult.message}",
            successCount = uploadResult.successCount + downloadResult.successCount,
            errorCount = uploadResult.errorCount + downloadResult.errorCount
        )
    }
    
    fun startRealtimeSync(onUpdate: ((Int) -> Unit)? = null) {
        val userId = authManager.getCurrentUserId()
        if (userId == null) {
            Log.w(TAG, "Cannot start realtime sync: user not authenticated")
            return
        }
        
        val currentDeviceId = DeviceInfo.getDeviceId(context)
        
        realtimeListener = firestore.collection(COLLECTION_RECORDS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Realtime sync error: ${error.message}")
                    return@addSnapshotListener
                }
                
                snapshot?.documentChanges?.forEach { change ->
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            val data = change.document.data
                            if (data != null) {
                                val record = mapToRecord(change.document.id, data)
                                // Пропустити записи з поточного пристрою
                                if (record.deviceId != currentDeviceId) {
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        val isDuplicate = checkForDuplicates(record)
                                        if (!isDuplicate) {
                                            database.stabilityDao().insert(record)
                                            onUpdate?.invoke(1)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        
        Log.d(TAG, "Real-time sync started")
    }
    
    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
        Log.d(TAG, "Real-time sync stopped")
    }
    
    suspend fun deleteRecordsFromCloudByTimeRange(beforeTime: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            val userId = authManager.getCurrentUserId()
            if (userId == null) {
                return@withContext SyncResult(false, "User not authenticated", 0, 0)
            }
            
            var successCount = 0
            var errorCount = 0
            
            val querySnapshot = if (beforeTime == 0L) {
                firestore.collection(COLLECTION_RECORDS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
            } else {
                firestore.collection(COLLECTION_RECORDS)
                    .whereEqualTo("userId", userId)
                    .whereLessThan("timestamp", beforeTime)
                    .get()
                    .await()
            }
            
            for (document in querySnapshot.documents) {
                try {
                    document.reference.delete().await()
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting document ${document.id}: ${e.message}", e)
                    errorCount++
                }
            }
            
            SyncResult(
                success = errorCount == 0,
                message = "Deleted $successCount records from cloud",
                successCount = successCount,
                errorCount = errorCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Delete from cloud failed: ${e.message}", e)
            SyncResult(false, "Delete failed: ${e.message}", 0, 1)
        }
    }
    
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val successCount: Int,
        val errorCount: Int
    )
}

