package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY rule_name ASC")
    fun getAllRules(): Flow<List<AutomationRule>>
    
    @Query("SELECT * FROM automation_rules WHERE rule_id = :ruleId")
    suspend fun getRule(ruleId: String): AutomationRule?
    
    @Query("SELECT * FROM automation_rules WHERE enabled = 1")
    fun getEnabledRules(): Flow<List<AutomationRule>>
    
    @Query("SELECT * FROM automation_rules WHERE synced = 0")
    suspend fun getUnsyncedRules(): List<AutomationRule>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule)
    
    @Update
    suspend fun updateRule(rule: AutomationRule)
    
    @Delete
    suspend fun deleteRule(rule: AutomationRule)
    
    @Query("UPDATE automation_rules SET synced = 1 WHERE rule_id = :ruleId")
    suspend fun markAsSynced(ruleId: String)
    
    @Query("DELETE FROM automation_rules")
    suspend fun deleteAll()
}

