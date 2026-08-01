package com.example.smsrelay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room 编译期生成该接口的实现；所有写操作使用 suspend，避免阻塞主线程。 */
@Dao
interface SmsDao {
    // 重复主键返回 -1；忽略冲突让接收短信的流程保持幂等。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: SmsEntity): Long

    // Flow 会在表数据变化时重新发射，Compose 界面因而自动更新。
    @Query("SELECT * FROM received_sms ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM received_sms WHERE uploadedAt IS NULL ORDER BY receivedAt LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<SmsEntity>

    @Query("UPDATE received_sms SET uploadedAt = :uploadedAt, lastUploadError = NULL WHERE clientMessageId = :id")
    suspend fun markUploaded(id: String, uploadedAt: Long)

    @Query("UPDATE received_sms SET lastUploadError = :error WHERE clientMessageId = :id")
    suspend fun recordUploadError(id: String, error: String)
}
