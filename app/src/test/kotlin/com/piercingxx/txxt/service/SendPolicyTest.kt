package com.piercingxx.txxt.service

import org.junit.Assert.assertFalse
import org.junit.Test

class SendPolicyTest {

    @Test
    fun `an outgoing message never requests a delivery report`() {
        assertFalse(SendPolicy.requestsDeliveryReport())
    }

    @Test
    fun `an outgoing message never requests a read report`() {
        assertFalse(SendPolicy.requestsReadReport())
    }

    @Test
    fun `remote MMS content is never downloaded automatically`() {
        assertFalse(SendPolicy.autoDownloadMms())
    }
}