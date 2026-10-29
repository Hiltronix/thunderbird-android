package com.fsck.k9.mail.helpers;


import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;


/**
 * Legacy-named test-only trust manager that permits exactly one pinned fixture
 * certificate. Test sockets must not silently trust unrelated TLS servers.
 */
class VeryTrustingTrustManager implements X509TrustManager {
    private final X509Certificate serverCertificate;


    public VeryTrustingTrustManager(X509Certificate serverCertificate) {
        this.serverCertificate = serverCertificate;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Client certificates are not accepted by the test trust manager");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0 || chain[0] == null ||
                !serverCertificate.equals(chain[0])) {
            throw new CertificateException("Unrecognized test server certificate");
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[] { serverCertificate };
    }
}

