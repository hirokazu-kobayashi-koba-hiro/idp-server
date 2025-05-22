/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.random;

import java.security.SecureRandom;
import org.idp.server.basic.base64.Base64Codeable;

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
