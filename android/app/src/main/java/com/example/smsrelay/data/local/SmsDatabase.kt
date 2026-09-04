package com.example.smsrelay.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 应用的 Room 数据库。导出 schema 便于课程中演示版本迁移和结构审查。 */
@Database(entities = [SmsEntity::class], version = 3, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        // 只加可空字段：升级后旧短信及待上传状态全部保留。
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE received_sms ADD COLUMN recipient TEXT")
            }
        }
        // 用联合索引同时满足过滤和排序；只替换索引，不删除表或短信。
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_received_sms_receivedAt_clientMessageId ON received_sms(receivedAt, clientMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_received_sms_uploadedAt_receivedAt_clientMessageId ON received_sms(uploadedAt, receivedAt, clientMessageId)")
                db.execSQL("DROP INDEX IF EXISTS index_received_sms_receivedAt")
                db.execSQL("DROP INDEX IF EXISTS index_received_sms_uploadedAt")
            }
        }
        // 双重检查 + volatile 保证整个进程只创建一个数据库实例。
        @Volatile private var instance: SmsDatabase? = null

        fun get(context: Context): SmsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SmsDatabase::class.java,
                "sms-relay.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
