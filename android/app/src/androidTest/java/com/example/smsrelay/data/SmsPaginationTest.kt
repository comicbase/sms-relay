package com.example.smsrelay.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsrelay.data.local.SmsDatabase
import com.example.smsrelay.data.local.SmsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsPaginationTest {
    @Test fun pagesAreBoundedStableAndCountsCoverAllRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SmsDatabase::class.java).build()
        try {
            val dao = db.smsDao()
            // 同一时间大量短信跨越页边界；不使用真实短信或联网。
            for (i in 0 until 105) dao.insert(SmsEntity("id-%04d".format(i), "test", "test body", 100, null, null, uploadedAt = if (i % 2 == 0) 200 else null))
            val first = dao.observeFirstPage(21).first()
            assertEquals(21, first.size)
            assertEquals(105, dao.observeTotalCount().first())
            assertEquals(52, dao.observePendingCount().first())
            val boundary = first[19]
            val second = dao.observePageBefore(boundary.receivedAt, boundary.clientMessageId, 21).first().take(20)
            assertTrue(first.take(20).map { it.clientMessageId }.intersect(second.map { it.clientMessageId }.toSet()).isEmpty())
            dao.insert(SmsEntity("new", "test", "new body", 101, null, null))
            assertEquals(second, dao.observePageBefore(boundary.receivedAt, boundary.clientMessageId, 21).first().take(20))
            val ids = mutableListOf<String>()
            var rows = dao.observeFirstPage(21).first()
            while (rows.isNotEmpty()) {
                val page = rows.take(20)
                ids.addAll(page.map { it.clientMessageId })
                if (rows.size <= 20) break
                val last = page.last()
                rows = dao.observePageBefore(last.receivedAt, last.clientMessageId, 21).first()
            }
            assertEquals(106, ids.size)
            assertEquals(106, ids.toSet().size)
            assertEquals(50, dao.pending().size)
            val plan = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM received_sms WHERE uploadedAt IS NULL ORDER BY receivedAt, clientMessageId LIMIT 50").use { cursor ->
                buildString { while (cursor.moveToNext()) append(cursor.getString(3)) }
            }
            assertTrue(plan, plan.contains("index_received_sms_uploadedAt_receivedAt_clientMessageId"))
            assertFalse(plan, plan.contains("TEMP B-TREE"))
        } finally { db.close() }
    }

    @Test fun versionTwoMigrationPreservesRecipientsAndUploadState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "pagination-migration-${System.nanoTime()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE received_sms (clientMessageId TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, body TEXT NOT NULL, receivedAt INTEGER NOT NULL, subscriptionId INTEGER, simSlot INTEGER, recipient TEXT, uploadedAt INTEGER, lastUploadError TEXT)")
            db.execSQL("CREATE INDEX index_received_sms_receivedAt ON received_sms(receivedAt)")
            db.execSQL("CREATE INDEX index_received_sms_uploadedAt ON received_sms(uploadedAt)")
            db.execSQL("INSERT INTO received_sms VALUES ('old', 'test', 'test', 100, 1, 0, '+10000000000', NULL, 'offline')")
            db.version = 2
        }
        val db = Room.databaseBuilder(context, SmsDatabase::class.java, name).addMigrations(SmsDatabase.MIGRATION_2_3).build()
        try {
            val row = db.smsDao().pending().single()
            assertEquals("+10000000000", row.recipient)
            assertEquals("offline", row.lastUploadError)
            assertEquals(1, db.smsDao().observeTotalCount().first())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
