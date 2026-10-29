package com.fsck.k9.mail

import assertk.assertFailure
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerSettingsTest {
    @Test
    fun `creating typical ServerSettings should not throw`() {
        ServerSettings(
            type = "imap",
            host = "imap.domain.example",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = "123456",
            clientCertificateAlias = null,
        )
    }

    @Test
    fun `diagnostic string hides account details and credentials`() {
        val settings = ServerSettings(
            type = "imap",
            host = "private-mail.example",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "private-username",
            password = "secret-credential",
            clientCertificateAlias = "private-certificate",
            extra = mapOf("oauth" to "secret-access-token"),
        )

        val diagnostics = settings.toString()

        assertTrue(diagnostics.contains("ServerSettings(type=imap"))
        assertFalse(diagnostics.contains("private-mail.example"))
        assertFalse(diagnostics.contains("private-username"))
        assertFalse(diagnostics.contains("secret-credential"))
        assertFalse(diagnostics.contains("private-certificate"))
        assertFalse(diagnostics.contains("secret-access-token"))
    }

    @Test
    fun `type that is not all lower case should throw`() {
        assertFailure {
            ServerSettings(
                type = "IMAP",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user",
                password = "123456",
                clientCertificateAlias = null,
            )
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("type must be all lower case")
    }

    @Test
    fun `username containing LF should throw`() {
        assertFailure {
            ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user\nname",
                password = "123456",
                clientCertificateAlias = null,
            )
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("username must not contain line break")
    }

    @Test
    fun `username containing CR should throw`() {
        assertFailure {
            ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user\rname",
                password = "123456",
                clientCertificateAlias = null,
            )
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("username must not contain line break")
    }

    @Test
    fun `password containing LF should throw`() {
        assertFailure {
            ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user",
                password = "123456\n",
                clientCertificateAlias = null,
            )
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("password must not contain line break")
    }

    @Test
    fun `password containing CR should throw`() {
        assertFailure {
            ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user",
                password = "123456\r",
                clientCertificateAlias = null,
            )
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("password must not contain line break")
    }
}
