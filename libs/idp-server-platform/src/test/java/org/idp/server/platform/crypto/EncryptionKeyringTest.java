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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class EncryptionKeyringTest {

  static final String K1 = key(1);
  static final String K2 = key(2);

  static String key(int fill) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, (byte) fill);
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Test
  void hkdfMatchesRfc5869TestCase1() {
    HexFormat hex = HexFormat.of();
    byte[] okm =
        HkdfSha256.derive(
            hex.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            hex.parseHex("000102030405060708090a0b0c"),
            hex.parseHex("f0f1f2f3f4f5f6f7f8f9"),
            42);
    assertEquals(
        "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
        hex.formatHex(okm));
  }

  @Test
  void withoutKeyringTheSingleKeyIsTheDefault() {
    EncryptionKeyring keyring = EncryptionKeyring.of("", null, K1);
    assertEquals("default", keyring.activeKeyId());
    KeyedAesCipher cipher = keyring.cipher("account-linking-token");
    assertEquals("secret", cipher.decrypt("default", cipher.encrypt("secret")));
  }

  @Test
  void rotationKeepsOldDataReadable() {
    KeyedAesCipher before = EncryptionKeyring.of("k1:" + K1, "k1", null).cipher("p");
    EncryptedData old = before.encrypt("old");

    // Step 1: k2 is handed out as passive. Nothing changes for writes yet.
    KeyedAesCipher passive = EncryptionKeyring.of("k1:" + K1 + ",k2:" + K2, "k1", null).cipher("p");
    assertEquals("k1", passive.activeKeyId());
    assertEquals("old", passive.decrypt("k1", old));

    // Step 2: k2 becomes active. Old data still opens; new data is under k2.
    KeyedAesCipher after = EncryptionKeyring.of("k2:" + K2 + ",k1:" + K1, "k2", null).cipher("p");
    assertEquals("old", after.decrypt("k1", old));
    assertEquals("k2", after.activeKeyId());
    EncryptedData fresh = after.encrypt("new");
    assertEquals("new", after.decrypt("k2", fresh));
    assertThrows(AesCryptoRuntimeException.class, () -> after.decrypt("k1", fresh));
  }

  @Test
  void removedKeyIsRefusedByName() {
    EncryptedData old = EncryptionKeyring.of("k1:" + K1, "k1", null).cipher("p").encrypt("old");
    KeyedAesCipher withoutK1 = EncryptionKeyring.of("k2:" + K2, "k2", null).cipher("p");
    AesCryptoRuntimeException exception =
        assertThrows(AesCryptoRuntimeException.class, () -> withoutK1.decrypt("k1", old));
    assertTrue(exception.getMessage().contains("k1"));
  }

  @Test
  void purposesDoNotShareKeys() {
    EncryptionKeyring keyring = EncryptionKeyring.of("k1:" + K1, "k1", null);
    EncryptedData data = keyring.cipher("account-linking-token").encrypt("secret");
    assertThrows(
        AesCryptoRuntimeException.class, () -> keyring.cipher("other").decrypt("k1", data));
    // Nor is the derived key the raw key that AesCipher would use directly.
    assertThrows(AesCryptoRuntimeException.class, () -> new AesCipher(K1).decrypt(data));
  }

  @Test
  void configurationErrorsFailAtStartup() {
    assertThrows(
        AesCryptoRuntimeException.class, () -> EncryptionKeyring.of("k1:" + K1, null, null));
    assertThrows(
        AesCryptoRuntimeException.class, () -> EncryptionKeyring.of("k1:" + K1, "k2", null));
    assertThrows(
        AesCryptoRuntimeException.class,
        () -> EncryptionKeyring.of("k1:" + K1 + ",k1:" + K2, "k1", null));
    assertThrows(AesCryptoRuntimeException.class, () -> EncryptionKeyring.of("k1", "k1", null));
    assertThrows(
        AesCryptoRuntimeException.class,
        () ->
            EncryptionKeyring.of(
                "k1:" + Base64.getEncoder().encodeToString(new byte[15]), "k1", null));
  }

  @Test
  void acceptsShorterExistingKeysAndStillDerivesAes256() {
    for (int length : new int[] {16, 24, 32, 48}) {
      String key = Base64.getEncoder().encodeToString(new byte[length]);
      KeyedAesCipher cipher = EncryptionKeyring.of("", null, key).cipher("p");
      assertEquals("secret", cipher.decrypt("default", cipher.encrypt("secret")));
    }
  }
}
