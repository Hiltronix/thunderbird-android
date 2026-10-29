package com.fsck.k9.mail.helpers;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.Test;

public class VeryTrustingTrustManagerTest {
    private final X509Certificate expected = KeyStoreProvider.getInstance().getServerCertificate();
    private final VeryTrustingTrustManager manager = new VeryTrustingTrustManager(expected);

    @Test
    public void recognizesConfiguredFixtureCertificate() throws CertificateException {
        manager.checkServerTrusted(new X509Certificate[] { expected }, "RSA");
    }

    @Test(expected = CertificateException.class)
    public void rejectsEmptyServerCertificateChain() throws CertificateException {
        manager.checkServerTrusted(new X509Certificate[0], "RSA");
    }

    @Test(expected = CertificateException.class)
    public void rejectsNullServerCertificate() throws CertificateException {
        manager.checkServerTrusted(new X509Certificate[] { null }, "RSA");
    }

    @Test(expected = CertificateException.class)
    public void rejectsClientCertificates() throws CertificateException {
        manager.checkClientTrusted(new X509Certificate[] { expected }, "RSA");
    }
}
