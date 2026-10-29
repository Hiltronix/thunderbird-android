package com.fsck.k9.mail.ssl

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.After
import org.junit.Test

class LocalKeyStoreTest {
    private val host = "mail.domain.example"
    private val port = 587

    private val tempDirectory = Files.createTempDirectory("KeyStore").toFile()
    private val localKeyStore = LocalKeyStore(directoryProvider = { tempDirectory })

    @After
    fun tearDown() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun `empty LocalKeyStore should not consider certificate valid`() {
        val certificate = readCertificate(host)

        val isCertificateValid = localKeyStore.isValidCertificate(certificate, host, port)

        assertThat(isCertificateValid).isFalse()
    }

    @Test
    fun `stored certificate should be considered valid`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)

        val isCertificateValid = localKeyStore.isValidCertificate(certificate, host, port)

        assertThat(isCertificateValid).isTrue()
    }

    @Test
    fun `certificate persists after reopening the local keystore`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)

        val reopened = LocalKeyStore(directoryProvider = { tempDirectory })

        assertThat(reopened.isValidCertificate(certificate, host, port)).isTrue()
    }

    @Test
    fun `successive certificate writes preserve previously saved entries`() {
        val first = readCertificate(host)
        val second = readCertificate("mail.another-domain.example")
        localKeyStore.addCertificate(host, port, first)
        localKeyStore.addCertificate("mail.another-domain.example", port, second)

        val reopened = LocalKeyStore(directoryProvider = { tempDirectory })

        assertThat(reopened.isValidCertificate(first, host, port)).isTrue()
        assertThat(reopened.isValidCertificate(second, "mail.another-domain.example", port)).isTrue()
    }

    @Test
    fun `completed certificate updates leave no temporary keystore files`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)

        val temporaryFiles = tempDirectory.listFiles().orEmpty()
            .filter { it.name.startsWith("KeyStore_v1.bks.") && it.name.endsWith(".tmp") }

        assertThat(temporaryFiles.isEmpty()).isTrue()
    }

    @Test
    fun `a stale incomplete write does not replace the last saved keystore`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)
        java.io.File(tempDirectory, "KeyStore_v1.bks.interrupted.tmp").writeText("incomplete")

        val reopened = LocalKeyStore(directoryProvider = { tempDirectory })

        assertThat(reopened.isValidCertificate(certificate, host, port)).isTrue()
    }

    @Test
    fun `host names must match`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)

        val isCertificateValid = localKeyStore.isValidCertificate(certificate, "evil.domain.example", port)

        assertThat(isCertificateValid).isFalse()
    }

    @Test
    fun `port numbers must match`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)

        val isCertificateValid = localKeyStore.isValidCertificate(certificate, host, 123)

        assertThat(isCertificateValid).isFalse()
    }

    @Test
    fun `different certificate should not be considered valid`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)
        val anotherCertificate = readCertificate("mail.another-domain.example")

        val isCertificateValid = localKeyStore.isValidCertificate(anotherCertificate, host, port)

        assertThat(isCertificateValid).isFalse()
    }

    @Test
    fun `deleted certificate shouldn't be considered valid`() {
        val certificate = readCertificate(host)
        localKeyStore.addCertificate(host, port, certificate)
        localKeyStore.deleteCertificate(host, port)

        val isCertificateValid = localKeyStore.isValidCertificate(certificate, host, port)

        assertThat(isCertificateValid).isFalse()
    }

    private fun readCertificate(name: String): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        LocalKeyStoreTest::class.java.getResourceAsStream("/certificates/$name.pem")!!.let { inputStream ->
            return certificateFactory.generateCertificate(inputStream) as X509Certificate
        }
    }
}
