package org.idp.server.basic.random;

import org.idp.server.basic.base64.Base64Codeable;

import java.security.SecureRandom;

/**
 * RandomStringGenerator
 */
public class RandomStringGenerator implements Base64Codeable {

    SecureRandom secureRandom;
    byte[] bytes;
    public RandomStringGenerator(int keyLength) {
        this.secureRandom = new SecureRandom();
        this.bytes = new byte[keyLength];
        secureRandom.nextBytes(bytes);
    }


    public String generate() {
        return encodeWithUrlSafe(bytes);
    }
}
