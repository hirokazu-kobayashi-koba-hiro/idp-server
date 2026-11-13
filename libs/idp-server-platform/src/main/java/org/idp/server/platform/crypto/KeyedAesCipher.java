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

import java.util.Map;

/**
 * AES-GCM over a keyring: encrypts with the active key, decrypts with the key the data names.
 *
 * <p>The key id is stored next to the ciphertext. Data whose key id the ring does not hold is
 * refused rather than tried against every key, so a key removed too early shows up as an error
 * naming that key.
 */
public class KeyedAesCipher {

  Map<String, AesCipher> ciphers;
  String activeKeyId;

  KeyedAesCipher(Map<String, AesCipher> ciphers, String activeKeyId) {
    this.ciphers = ciphers;
    this.activeKeyId = activeKeyId;
  }

  /** Encrypts with the active key. Store {@link #activeKeyId()} with the result. */
  public EncryptedData encrypt(String plainText) {
    return ciphers.get(activeKeyId).encrypt(plainText);
  }

  /** Encrypts with the active key, binding the result to {@code additionalData}. */
  public EncryptedData encrypt(String plainText, byte[] additionalData) {
    return ciphers.get(activeKeyId).encrypt(plainText, additionalData);
  }

  public String decrypt(String keyId, EncryptedData data) {
    return decrypt(keyId, data, null);
  }

  public String decrypt(String keyId, EncryptedData data, byte[] additionalData) {
    AesCipher cipher = ciphers.get(keyId);
    if (cipher == null) {
      throw new AesCryptoRuntimeException("Encryption key is not in the keyring: " + keyId);
    }
    return cipher.decrypt(data, additionalData);
  }

  public String activeKeyId() {
    return activeKeyId;
  }
}
