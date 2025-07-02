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

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesCipher {

  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH = 128;
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  SecretKey secretKey;
  Base64.Encoder encoder;
  Base64.Decoder decoder;

  public AesCipher(String encryptionKey) {
    byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
    this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    this.encoder = Base64.getEncoder();
    this.decoder = Base64.getDecoder();
  }

  public EncryptedData encrypt(String plainText) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec spec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      return new EncryptedData(encoder.encodeToString(cipherText), encoder.encodeToString((iv)));
    } catch (InvalidAlgorithmParameterException
        | InvalidKeyException
        | IllegalBlockSizeException
        | BadPaddingException
        | NoSuchPaddingException
        | NoSuchAlgorithmException exception) {

      throw new AesCryptoRuntimeException("Error while encrypting data: " + plainText, exception);
    }
  }

  public String decrypt(EncryptedData data) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          secretKey,
          new GCMParameterSpec(TAG_LENGTH, decoder.decode(data.iv())));

      byte[] decrypted = cipher.doFinal(decoder.decode(data.cipherText()));

      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (InvalidAlgorithmParameterException
        | InvalidKeyException
        | IllegalBlockSizeException
        | BadPaddingException
        | NoSuchPaddingException
        | NoSuchAlgorithmException exception) {

      throw new AesCryptoRuntimeException("Error while decrypting data: " + data, exception);
    }
  }
}
