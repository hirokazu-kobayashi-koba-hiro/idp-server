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
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The deployment's encryption keys, each under a key id, and the one new data is encrypted with.
 *
 * <p>Every other key is passive: it still opens what it encrypted, but encrypts nothing new. A key
 * is rotated in two steps, so that no server encrypts with a key another server cannot open yet:
 * first every server is given the new key as a passive one, then the new key is made active.
 *
 * <p>Keys are never used as they are. {@link #cipher(String)} derives a separate key per purpose
 * from each of them.
 */
public class EncryptionKeyring {

  /** The key id of {@code ENCRYPTION_KEY} when no keyring is configured. */
  public static final String DEFAULT_KEY_ID = "default";

  /** The length of every derived key: AES-256. */
  private static final int KEY_LENGTH = 32;

  /**
   * The shortest key accepted as input. A configured key only seeds the derivation, so it need not
   * be an AES key size itself; 16 bytes keeps an existing AES-128 {@code ENCRYPTION_KEY} valid.
   */
  private static final int MIN_INPUT_KEY_LENGTH = 16;

  Map<String, byte[]> keys;
  String activeKeyId;

  EncryptionKeyring(Map<String, byte[]> keys, String activeKeyId) {
    this.keys = keys;
    this.activeKeyId = activeKeyId;
  }

  /**
   * Builds the keyring from its configuration.
   *
   * @param encryptionKeys {@code <key id>:<base64 key>} pairs separated by commas. When empty, the
   *     keyring holds {@code encryptionKey} alone, under {@link #DEFAULT_KEY_ID}.
   * @param activeKeyId the key new data is encrypted with. Required when {@code encryptionKeys} is
   *     set.
   * @param encryptionKey {@code ENCRYPTION_KEY}, used only when {@code encryptionKeys} is empty
   */
  public static EncryptionKeyring of(
      String encryptionKeys, String activeKeyId, String encryptionKey) {
    if (encryptionKeys == null || encryptionKeys.isBlank()) {
      Map<String, byte[]> keys = new LinkedHashMap<>();
      keys.put(DEFAULT_KEY_ID, decodeKey(DEFAULT_KEY_ID, encryptionKey));
      return new EncryptionKeyring(keys, DEFAULT_KEY_ID);
    }

    Map<String, byte[]> keys = new LinkedHashMap<>();
    for (String entry : encryptionKeys.split(",")) {
      String trimmed = entry.trim();
      int separator = trimmed.indexOf(':');
      if (separator <= 0) {
        throw new AesCryptoRuntimeException(
            "Each encryption key must be written as <key id>:<base64 key>.");
      }
      String keyId = trimmed.substring(0, separator);
      if (keys.containsKey(keyId)) {
        throw new AesCryptoRuntimeException("Duplicate encryption key id: " + keyId);
      }
      keys.put(keyId, decodeKey(keyId, trimmed.substring(separator + 1)));
    }
    if (activeKeyId == null || activeKeyId.isBlank()) {
      throw new AesCryptoRuntimeException(
          "The active encryption key id is required when encryption keys are configured.");
    }
    if (!keys.containsKey(activeKeyId)) {
      throw new AesCryptoRuntimeException(
          "The active encryption key id is not among the configured keys: " + activeKeyId);
    }
    return new EncryptionKeyring(Collections.unmodifiableMap(keys), activeKeyId);
  }

  private static byte[] decodeKey(String keyId, String encoded) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(encoded.trim());
    } catch (IllegalArgumentException exception) {
      throw new AesCryptoRuntimeException("Encryption key is not base64: " + keyId);
    }
    if (key.length < MIN_INPUT_KEY_LENGTH) {
      throw new AesCryptoRuntimeException(
          "Encryption key must be at least " + MIN_INPUT_KEY_LENGTH + " bytes: " + keyId);
    }
    return key;
  }

  /**
   * A cipher for one purpose, holding a key derived from every key in the ring.
   *
   * @param purpose names the use, e.g. {@code account-linking-token}. Data encrypted for one
   *     purpose cannot be decrypted for another.
   */
  public KeyedAesCipher cipher(String purpose) {
    byte[] info = ("idp-server/" + purpose).getBytes(StandardCharsets.UTF_8);
    Map<String, AesCipher> ciphers = new LinkedHashMap<>();
    keys.forEach(
        (keyId, key) ->
            ciphers.put(keyId, new AesCipher(HkdfSha256.derive(key, null, info, KEY_LENGTH))));
    return new KeyedAesCipher(ciphers, activeKeyId);
  }

  public String activeKeyId() {
    return activeKeyId;
  }

  public Set<String> keyIds() {
    return keys.keySet();
  }
}
