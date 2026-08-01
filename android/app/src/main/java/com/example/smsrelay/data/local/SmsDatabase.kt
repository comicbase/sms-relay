package com.example.smsrelay.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 应用的 Room 数据库。导出 schema 便于课程中演示版本迁移和结构审查。 */
@Database(entities = [SmsEntity::class], version = 1, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        // 双重检查 + volatile 保证整个进程只创建一个数据库实例。
        @Volatile private var instance: SmsDatabase? = null

        fun get(context: Context): SmsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SmsDatabase::class.java,
                "sms-relay.db",
            ).build().also { instance = it }
        }
    }
}
