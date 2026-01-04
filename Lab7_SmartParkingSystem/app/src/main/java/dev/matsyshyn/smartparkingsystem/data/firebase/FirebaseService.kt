package dev.matsyshyn.smartparkingsystem.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import kotlinx.coroutines.tasks.await

class FirebaseService {
    private val db = FirebaseFirestore.getInstance()
    
    private val SENSOR_DATA_COLLECTION = "sensor_data"
    private val DEVICE_STATE_COLLECTION = "device_states"
    private val AUTOMATION_RULES_COLLECTION = "automation_rules"
    
    // ========== Sensor Data ==========
    
    suspend fun uploadSensorData(sensorData: SensorData): Result<Unit> {
        return try {
            val data = sensorData.toFirestoreMap()
            // Використовуємо timestamp як унікальний ідентифікатор для синхронізації між пристроями
            val documentId = sensorData.timestamp.toString()
            db.collection(SENSOR_DATA_COLLECTION)
                .document(documentId)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadSensorDataList(sensorDataList: List<SensorData>): Result<Unit> {
        return try {
            val batch = db.batch()
            sensorDataList.forEach { sensorData ->
                // Використовуємо timestamp як унікальний ідентифікатор
                val documentId = sensorData.timestamp.toString()
                val docRef = db.collection(SENSOR_DATA_COLLECTION)
                    .document(documentId)
                batch.set(docRef, sensorData.toFirestoreMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadSensorData(sinceTimestamp: Long = 0): Result<List<SensorData>> {
        return try {
            // Завантажуємо всі дані (або з певного timestamp)
            val query = if (sinceTimestamp > 0) {
                db.collection(SENSOR_DATA_COLLECTION)
                    .whereGreaterThanOrEqualTo("timestamp", sinceTimestamp)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
            } else {
                db.collection(SENSOR_DATA_COLLECTION)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
            }
            
            val snapshot = query.get().await()
            
            val sensorDataList = snapshot.documents.mapNotNull { doc ->
                try {
                    val map = doc.data ?: return@mapNotNull null
                    // ID не важливий для синхронізації, використовуємо timestamp
                    SensorData.fromFirestoreMap(map).copy(id = 0L) // ID буде згенеровано при вставці
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(sensorDataList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== Device States ==========
    
    suspend fun uploadDeviceState(deviceState: DeviceState): Result<Unit> {
        return try {
            val data = deviceState.toFirestoreMap()
            db.collection(DEVICE_STATE_COLLECTION)
                .document(deviceState.deviceId)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadDeviceStateList(deviceStateList: List<DeviceState>): Result<Unit> {
        return try {
            val batch = db.batch()
            deviceStateList.forEach { deviceState ->
                val docRef = db.collection(DEVICE_STATE_COLLECTION)
                    .document(deviceState.deviceId)
                batch.set(docRef, deviceState.toFirestoreMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadDeviceStates(): Result<List<DeviceState>> {
        return try {
            val snapshot = db.collection(DEVICE_STATE_COLLECTION)
                .get()
                .await()
            
            val deviceStateList = snapshot.documents.mapNotNull { doc ->
                try {
                    val map = doc.data ?: return@mapNotNull null
                    DeviceState.fromFirestoreMap(map)
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(deviceStateList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== Automation Rules ==========
    
    suspend fun uploadAutomationRule(rule: AutomationRule): Result<Unit> {
        return try {
            val data = rule.toFirestoreMap()
            db.collection(AUTOMATION_RULES_COLLECTION)
                .document(rule.ruleId)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadAutomationRuleList(rules: List<AutomationRule>): Result<Unit> {
        return try {
            val batch = db.batch()
            rules.forEach { rule ->
                val docRef = db.collection(AUTOMATION_RULES_COLLECTION)
                    .document(rule.ruleId)
                batch.set(docRef, rule.toFirestoreMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadAutomationRules(): Result<List<AutomationRule>> {
        return try {
            val snapshot = db.collection(AUTOMATION_RULES_COLLECTION)
                .get()
                .await()
            
            val rules = snapshot.documents.mapNotNull { doc ->
                try {
                    val map = doc.data ?: return@mapNotNull null
                    AutomationRule.fromFirestoreMap(map)
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== Connection Check ==========
    
    suspend fun checkConnection(): Boolean {
        return try {
            db.collection("connection_test")
                .document("test")
                .get()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== Database Management ==========
    
    /**
     * Очистити всі дані з Firebase (для тестування)
     * Увага: Firebase має обмеження на кількість операцій в batch (500)
     */
    suspend fun clearAllFirebaseData(): Result<Unit> {
        return try {
            val collections = listOf(
                SENSOR_DATA_COLLECTION,
                DEVICE_STATE_COLLECTION,
                AUTOMATION_RULES_COLLECTION
            )
            
            collections.forEach { collectionName ->
                val snapshot = db.collection(collectionName).get().await()
                
                // Видаляємо частинами (по 500 документів)
                snapshot.documents.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

