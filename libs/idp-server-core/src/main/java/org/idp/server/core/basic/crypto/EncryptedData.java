package org.idp.server.core.basic.crypto;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

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
