package dev.aicli.app.ui.providers

import org.junit.Assert.*
import org.junit.Test

class AuthenticationLinksTest {
    @Test fun extractsCompleteLinksWithoutTerminalColorCodes() {
        assertEquals(listOf("https://example.invalid/auth?state=hello"),
            terminalLinks("Visit \u001B[34mhttps://example.invalid/auth?state=hello\u001B[0m\r\n"))
        assertTrue(terminalLinks("Visit https://example.invalid/incomplete").isEmpty())
    }
    @Test fun removesDuplicatesAndRejectsNonHttpsSchemes() {
        assertEquals(listOf("https://example.invalid/"), terminalLinks("javascript:alert(1) https://example.invalid/ \nhttps://example.invalid/ \n"))
    }
}
