package com.example.cookbook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cookbook-test-session"
    private val store = SessionStore(context, name)
    private val path = File(context.noBackupFilesDir, name)

    @Before
    @After
    fun clearTestSession() = store.clear()

    @Test
    fun encryptedTokenCanBeReopenedAndRemoved() {
        val token = "test-token-without-user-data"
        store.save(token)
        val firstPayload = path.readBytes()
        assertFalse(firstPayload.toString(Charsets.ISO_8859_1).contains(token))
        assertEquals(token, SessionStore(context, name).load())

        store.save(token)
        assertFalse(firstPayload.contentEquals(path.readBytes()))
        store.clear()
        assertNull(SessionStore(context, name).load())
    }

    @Test
    fun alteredCiphertextIsDiscarded() {
        store.save("test-session-for-tamper-check")
        val payload = path.readBytes()
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        path.writeBytes(payload)

        assertNull(store.load())
        assertFalse(path.exists())
    }

    @Test
    fun cloudSessionIsBoundToItsApiOrigin() {
        val origin = "https://first.example.test"
        val token = "dummy-token-for-origin-test"
        SessionStore(context, name, origin).save(token)
        assertEquals(token, SessionStore(context, name, origin).load())
        assertNull(SessionStore(context, name, "https://second.example.test").load())
    }
}
