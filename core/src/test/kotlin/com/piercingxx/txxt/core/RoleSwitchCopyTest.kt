package com.piercingxx.txxt.core

import org.junit.Assert.assertTrue
import org.junit.Test

class RoleSwitchCopyTest {

    @Test
    fun `every role-switch string warns that the archive dies without export`() {
        val copies = listOf(
            RoleSwitchCopy.ARCHIVE_DIES,
            RoleSwitchCopy.FIRST_RUN,
            RoleSwitchCopy.UNSET,
            RoleSwitchCopy.REVOKED,
        )
        copies.forEach { copy ->
            assertTrue("must mention export: $copy", copy.contains("Export", ignoreCase = true))
            assertTrue("must mention the archive: $copy", copy.contains("archive", ignoreCase = true))
        }
    }

    @Test
    fun `first-run copy says TxxT must be the default SMS app`() {
        assertTrue(RoleSwitchCopy.FIRST_RUN.contains("default SMS app"))
    }
}
