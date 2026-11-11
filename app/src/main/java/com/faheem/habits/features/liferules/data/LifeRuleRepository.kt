package com.faheem.habits.features.liferules.data

import com.faheem.habits.core.data.LifeRule
import com.faheem.habits.core.data.LifeRuleDao
import com.faheem.habits.core.data.LifeRuleFile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LifeRuleRepository @Inject constructor(
    private val dao: LifeRuleDao
) {
    val rules: Flow<List<LifeRule>> = dao.getAll()

    suspend fun save(rule: LifeRule): Int {
        if (rule.id == 0) return dao.insert(rule).toInt()
        dao.update(rule)
        return rule.id
    }

    suspend fun delete(rule: LifeRule) = dao.delete(rule)
    suspend fun addFile(file: LifeRuleFile) = dao.insertFile(file)
    suspend fun deleteFile(file: LifeRuleFile) = dao.markFileDeleted(file.id, System.currentTimeMillis())
    fun files(ruleId: Int): Flow<List<LifeRuleFile>> = dao.getFiles(ruleId)
}
