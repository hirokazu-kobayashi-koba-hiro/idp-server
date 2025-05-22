/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
