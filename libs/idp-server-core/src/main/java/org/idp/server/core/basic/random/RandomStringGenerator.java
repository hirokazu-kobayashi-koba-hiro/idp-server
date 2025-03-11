package org.idp.server.core.basic.random;

import java.security.SecureRandom;
import org.idp.server.core.basic.base64.Base64Codeable;

/** RandomStringGenerator */
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
