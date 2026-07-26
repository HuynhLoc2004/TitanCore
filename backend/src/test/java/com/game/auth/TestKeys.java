package com.game.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

final class TestKeys {

    private TestKeys() {
    }

    static KeyPair generateRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String privatePem(KeyPair keyPair) {
        return pem("PRIVATE KEY", ((RSAPrivateKey) keyPair.getPrivate()).getEncoded());
    }

    static String publicPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", ((RSAPublicKey) keyPair.getPublic()).getEncoded());
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)).encodeToString(encoded)
                + "\n-----END " + label + "-----";
    }
}
