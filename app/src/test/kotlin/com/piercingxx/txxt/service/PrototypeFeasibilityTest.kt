package com.piercingxx.txxt.service

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PROTOTYPE — validates that a plain JVM unit test can drive the receivers'
 * onReceive entry points via MockK (mockkStatic on the mockable android.jar
 * Telephony class, mockkObject on SendPipeline). Deleted after feasibility is
 * confirmed; not a deliverable.
 */
class PrototypeFeasibilityTest {

    @Test
    fun `drives SmsReceiver onReceive through the auto-reply decision`() {
        mockkStatic(Telephony.Sms.Intents::class)
        val smsMessage = mockk<android.telephony.SmsMessage>()
        every { smsMessage.originatingAddress } returns "+15550001111"
        every { smsMessage.messageBody } returns "hello"
        every {
            Telephony.Sms.Intents.getMessagesFromIntent(any())
        } returns arrayOf(smsMessage)

        mockkObject(SendPipeline)
        every { SendPipeline.sendSms(any(), any(), any()) } returns Unit

        val context = mockk<Context>()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        SmsReceiver().onReceive(context, intent)

        // Auto-reply is off by default, so sendSms must NOT be invoked.
        verify(exactly = 0) { SendPipeline.sendSms(any(), any(), any()) }
    }

    @Test
    fun `drives MmsReceiver onReceive through the audio drop`() {
        val receiver = spyk(MmsReceiver())
        val context = mockk<Context>()
        val intent = mockk<Intent>()
        every { intent.action } returns "android.provider.Telephony.WAP_PUSH_RECEIVED"
        every { intent.type } returns "audio/mpeg"
        receiver.onReceive(context, intent)
        verify(exactly = 1) { receiver.abortBroadcast() }
    }

    @Test
    fun `drives SendPipeline sendSms and asserts null report intents`() {
        mockkStatic(SmsManager::class)
        val smsManager = mockk<SmsManager>()
        every { SmsManager.getDefault() } returns smsManager
        every {
            smsManager.sendTextMessage(any(), any(), any(), any(), any())
        } returns Unit

        SendPipeline.sendSms(mockk(), "+15550001111", "hi")

        verify {
            smsManager.sendTextMessage(
                "+15550001111", null, "hi", null, null,
            )
        }
    }

    @Test
    fun `manifest-declared receiver class names resolve to the classes`() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val smsDeclared = manifest.contains("android:name=\".service.SmsReceiver\"")
        val mmsDeclared = manifest.contains("android:name=\".service.MmsReceiver\"")
        assertEquals(true, smsDeclared)
        assertEquals(true, mmsDeclared)
        // Resolve the declared names to real classes on the classpath.
        assertEquals(
            SmsReceiver::class.java,
            Class.forName("com.piercingxx.txxt.service.SmsReceiver"),
        )
        assertEquals(
            MmsReceiver::class.java,
            Class.forName("com.piercingxx.txxt.service.MmsReceiver"),
        )
    }
}