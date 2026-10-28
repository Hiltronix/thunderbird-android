package com.fsck.k9.mail.ssl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects the compromised eMudhra public keys before the normal trust and
 * user-approved-certificate fallback checks.
 */
final class CertificateBlocklist {
    private static final Set<String> BLOCKED_SPKI_SHA256 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
            "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
            "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
            "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
            "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
            "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
            "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
            "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6"
    )));

    private CertificateBlocklist() {
    }

    static void rejectCompromisedPublicKeys(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("Empty server certificate chain");
        }

        final MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException("SHA-256 is not available", e);
        }

        for (X509Certificate certificate : chain) {
            if (certificate == null || certificate.getPublicKey() == null
                    || certificate.getPublicKey().getEncoded() == null) {
                throw new CertificateException("Server certificate is missing a public key");
            }

            String publicKeyHash = toHex(sha256.digest(certificate.getPublicKey().getEncoded()));
            if (isBlockedSpkiSha256(publicKeyHash)) {
                throw new CertificateException("Server certificate chain contains a compromised public key");
            }
        }
    }

    static boolean isBlockedSpkiSha256(String sha256Hex) {
        return sha256Hex != null && BLOCKED_SPKI_SHA256.contains(sha256Hex.toLowerCase(Locale.ROOT));
    }

    private static String toHex(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte element : digest) {
            result.append(Character.forDigit((element >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(element & 0x0f, 16));
        }
        return result.toString();
    }
}
