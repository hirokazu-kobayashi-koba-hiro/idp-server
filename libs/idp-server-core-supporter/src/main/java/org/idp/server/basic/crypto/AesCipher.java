/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.idp.server.basic.base64.Base64Codeable;

public class AesCipher implements Base64Codeable {

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
