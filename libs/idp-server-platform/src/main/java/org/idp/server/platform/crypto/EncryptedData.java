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

public class EncryptedData implements JsonReadable {

  String cipherText;
  String iv;

  public EncryptedData() {}

  public EncryptedData(String cipherText, String iv) {
    this.cipherText = cipherText;
    this.iv = iv;
  }

  public String cipherText() {
    return cipherText;
  }

  public String iv() {
    return iv;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("cipher_text", cipherText);
    result.put("iv", iv);
    return result;
  }
}
