/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.crypto;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

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
