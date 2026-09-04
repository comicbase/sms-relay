package com.example.smsrelay.data

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsrelay.data.local.SmsDatabase
import com.example.smsrelay.data.local.SmsEntity
import com.example.smsrelay.receiver.SmsSimMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipientPersistenceTest {
    @Test fun migrationPreservesOldMessagesAndNewRecipients() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recipient-migration-test-${System.nanoTime()}.db"
        // 重建 v1 字段与索引；打开 v3 时 Room 同时检查整条迁移链后的 schema。
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE received_sms (clientMessageId TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, body TEXT NOT NULL, receivedAt INTEGER NOT NULL, subscriptionId INTEGER, simSlot INTEGER, uploadedAt INTEGER, lastUploadError TEXT)")
            db.execSQL("CREATE INDEX index_received_sms_receivedAt ON received_sms(receivedAt)")
            db.execSQL("CREATE INDEX index_received_sms_uploadedAt ON received_sms(uploadedAt)")
            db.execSQL("INSERT INTO received_sms VALUES ('old', '10000', 'test', 1, 4, 0, NULL, 'offline')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, SmsDatabase::class.java, name)
            .addMigrations(SmsDatabase.MIGRATION_1_2, SmsDatabase.MIGRATION_2_3).build()
        try {
            val old = database.smsDao().pending().single()
            assertNull(old.recipient)
            assertEquals("offline", old.lastUploadError)
            val message = SmsEntity("new", "10001", "test only", 2, 5, 1, recipient = "+819000000000")
            database.smsDao().insert(message)
            assertEquals("+819000000000", database.smsDao().pending().last().recipient)
            database.smsDao().markUploaded("new", 3)
            assertEquals(listOf("old"), database.smsDao().pending().map { it.clientMessageId })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun metadataUsesStandardThenLegacyKeysAndNeverGuesses() {
        assertNull(SmsSimMetadata.slot(Intent()))
        assertNull(SmsSimMetadata.slot(Intent().putExtra("slot", -1)))
        assertEquals(1, SmsSimMetadata.slot(Intent().putExtra("slot", 1)))
        val modern = Intent().putExtra("android.telephony.extra.SLOT_INDEX", 0).putExtra("slot", 1)
        assertEquals(0, SmsSimMetadata.slot(modern))
        assertEquals(8, SmsSimMetadata.subscriptionId(Intent().putExtra("subscription", 8)))
    }

    @Test fun savedConfigurationCanBeReadByANewStoreAndCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SimNumberStore(context)
        val previous = store.get()
        try {
            store.save(SimNumbers("+8613800000000", "09000000000"))
            assertEquals("09000000000", SimNumberStore(context).get().recipientFor(1))
            store.save(SimNumbers())
            assertNull(SimNumberStore(context).get().recipientFor(0))
        } finally {
            store.save(previous)
        }
    }
}
