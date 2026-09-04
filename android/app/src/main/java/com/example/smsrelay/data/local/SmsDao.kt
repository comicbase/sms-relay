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

    // 每次最多读取一页及一条探测记录，不将全部短信正文放入内存。
    @Query("SELECT * FROM received_sms ORDER BY receivedAt DESC, clientMessageId DESC LIMIT :limit")
    fun observeFirstPage(limit: Int): Flow<List<SmsEntity>>

    // 相同接收时间用指纹作第二排序键，避免分页时漏项或重复。
    @Query("SELECT * FROM received_sms WHERE receivedAt <= :time AND (receivedAt < :time OR clientMessageId < :id) ORDER BY receivedAt DESC, clientMessageId DESC LIMIT :limit")
    fun observePageBefore(time: Long, id: String, limit: Int): Flow<List<SmsEntity>>

    @Query("SELECT COUNT(*) FROM received_sms")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM received_sms WHERE uploadedAt IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM received_sms WHERE uploadedAt IS NULL ORDER BY receivedAt, clientMessageId LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<SmsEntity>

    @Query("UPDATE received_sms SET uploadedAt = :uploadedAt, lastUploadError = NULL WHERE clientMessageId = :id")
    suspend fun markUploaded(id: String, uploadedAt: Long)

    @Query("UPDATE received_sms SET lastUploadError = :error WHERE clientMessageId = :id")
    suspend fun recordUploadError(id: String, error: String)
}
