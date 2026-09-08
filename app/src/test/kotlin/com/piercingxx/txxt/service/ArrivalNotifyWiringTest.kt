package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArrivalNotifyWiringTest {

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/kotlin/com/piercingxx/txxt/$name"),
            File("app/src/main/kotlin/com/piercingxx/txxt/$name"),
        ).first { it.exists() }.readText()

    private val manifest: String
        get() = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.exists() }.readText()

    @Test
    fun `arrival notify skips a sender whose thread is already on screen`() {
        val arrival = source("service/ArrivalNotify.kt")
        assertTrue(arrival.contains("ViewedThread.isOpenFor(sender)"))
        assertTrue(arrival.contains("DialerGroups.BLOCKED"))
        assertTrue(arrival.contains("keysNamed"))
    }

    @Test
    fun `sms and mms deliver both post through ArrivalNotify`() {
        val sms = source("service/SmsDeliverReceiver.kt")
        val mms = source("service/MmsDeliverReceiver.kt")
        assertTrue(sms.contains("ArrivalNotify.post("))
        assertTrue(mms.contains("ArrivalNotify.post("))
    }

    @Test
    fun `manifest holds the dialer tier-sync permission`() {
        assertTrue(manifest.contains("com.piercingxx.xxdialer.permission.TIER_SYNC"))
        assertTrue(manifest.contains("uses-permission android:name=\"com.piercingxx.xxdialer.permission.TIER_SYNC\""))
    }

    @Test
    fun `dialer groups reader uses the family groups path`() {
        val groups = source("service/DialerGroups.kt")
        assertTrue(groups.contains("/groups"))
        assertTrue(groups.contains("COL_GROUP_NAME"))
        assertFalse(groups.contains("ContactsContract.Groups"))
    }
}
