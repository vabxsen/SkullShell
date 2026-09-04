package dev.aicli.core.logging

import org.junit.Assert.*
import org.junit.Test

class LoggerTest {
    @Test fun standaloneCredentialsAreRemovedCompletely() {
        val secrets = listOf("sk-proj-1234567890abc_hello", "ghp_1234567890abcdefghijklmn", "Bearer abc.def-123")
        for (secret in secrets) assertFalse(AppLog.redact("Received $secret").contains(secret))
    }
    @Test fun jsonAndEnvironmentCredentialsAreRemoved() {
        for (input in listOf("OPENAI_API_KEY=private-value", "\"access_token\":\"private-value\"", "password: private-value")) {
            assertFalse(AppLog.redact(input).contains("private-value"))
        }
    }
    @Test fun ordinaryDiagnosticTextIsPreserved() {
        assertEquals("Shell exited with 127", AppLog.redact("Shell exited with 127"))
    }
    @Test fun authorizationHeaderDoesNotLeakItsBearerValue() {
        assertFalse(AppLog.redact("Authorization: Bearer private.token-value").contains("private.token-value"))
    }
}
