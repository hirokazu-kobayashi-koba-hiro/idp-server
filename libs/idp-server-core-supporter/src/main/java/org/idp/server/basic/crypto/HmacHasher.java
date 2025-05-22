/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacHasher {

  private static final String HMAC_ALGO = "HmacSHA256";
  SecretKeySpec secretKey;

  public HmacHasher(String secret) {
    this.secretKey = new SecretKeySpec(secret.getBytes(), HMAC_ALGO);
  }

  public String hash(String input) {
    try {

      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(secretKey);
      byte[] hmac = mac.doFinal(input.getBytes());

      return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {

      throw new HmacHasherRuntimeException("Failed to compute HMAC", e);
    }
  }
}
