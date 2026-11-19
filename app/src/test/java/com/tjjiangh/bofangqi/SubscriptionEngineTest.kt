package com.tjjiangh.bofangqi

import com.tjjiangh.bofangqi.subscription.SubscriptionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionEngineTest {

    @Test
    fun buildApiUrl_preservesExistingQueryAndEscapesKeyword() {
        val result = SubscriptionEngine.buildApiUrl(
            apiUrl = "https://example.com/api.php?apikey=test",
            page = 2,
            keyword = "星际 旅行"
        )

        assertTrue(result.startsWith("https://example.com/api.php?apikey=test&"))
        assertTrue(result.contains("pg=2"))
        assertTrue(result.contains("wd=%E6%98%9F%E9%99%85+%E6%97%85%E8%A1%8C"))
    }

    @Test
    fun buildApiUrl_addsDetailAndPageParameters() {
        val result = SubscriptionEngine.buildApiUrl(
            apiUrl = "https://example.com/api.php",
            page = 3,
            typeId = "12"
        )

        assertEquals("https://example.com/api.php?ac=detail&pg=3&t=12", result)
    }
}
