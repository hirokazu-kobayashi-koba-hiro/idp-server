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
package org.idp.server.platform.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF with HMAC-SHA256 (RFC 5869).
 *
 * <p>Turns one secret into independent keys, one per purpose, so that a weakness or a leak in one
 * use of a key does not carry over to the others.
 */
public class HkdfSha256 {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int HASH_LENGTH = 32;

  public static byte[] derive(byte[] inputKey, byte[] salt, byte[] info, int length) {
    if (length <= 0 || length > 255 * HASH_LENGTH) {
      throw new AesCryptoRuntimeException("Invalid HKDF output length: " + length);
    }
    byte[] pseudoRandomKey =
        hmac(salt == null || salt.length == 0 ? new byte[HASH_LENGTH] : salt, inputKey);

    byte[] output = new byte[length];
    byte[] block = new byte[0];
    int offset = 0;
    for (int counter = 1; offset < length; counter++) {
      byte[] input = new byte[block.length + info.length + 1];
      System.arraycopy(block, 0, input, 0, block.length);
      System.arraycopy(info, 0, input, block.length, info.length);
      input[input.length - 1] = (byte) counter;
      block = hmac(pseudoRandomKey, input);
      int size = Math.min(block.length, length - offset);
      System.arraycopy(block, 0, output, offset, size);
      offset += size;
    }
    return output;
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key, ALGORITHM));
      return mac.doFinal(data);
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new AesCryptoRuntimeException("HKDF failed", exception);
    }
  }
}
