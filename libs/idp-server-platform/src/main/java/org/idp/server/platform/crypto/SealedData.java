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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Ciphertext encrypted under a key derived for this one message.
 *
 * <p>Unlike {@link EncryptedData}, which is encrypted directly under a long-lived key, this carries
 * the salt the message key was derived with. Every message gets a fresh key, so the AES-GCM limit
 * on random IVs per key never comes into play however many messages one data key encrypts.
 */
public class SealedData implements JsonReadable {

  /** The only format so far: HKDF-SHA256 message key, AES-256-GCM, 128-bit tag. */
  public static final int VERSION_1 = 1;

  int version;
  String salt;
  String iv;
  String cipherText;

  public SealedData() {}

  public SealedData(int version, String salt, String iv, String cipherText) {
    this.version = version;
    this.salt = salt;
    this.iv = iv;
    this.cipherText = cipherText;
  }

  public int version() {
    return version;
  }

  public String salt() {
    return salt;
  }

  public String iv() {
    return iv;
  }

  public String cipherText() {
    return cipherText;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("version", version);
    result.put("salt", salt);
    result.put("iv", iv);
    result.put("cipher_text", cipherText);
    return result;
  }
}
