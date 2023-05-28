package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;

public class JsonWebEncryptionDecrypter {

  JWEDecrypter decrypter;

  public JsonWebEncryptionDecrypter() {}

  public JsonWebEncryptionDecrypter(JWEDecrypter decrypter) {
    this.decrypter = decrypter;
  }

  public JsonWebSignature decrypt(JsonWebEncryption jsonWebEncryption) throws JoseInvalidException {
    try {
      jsonWebEncryption.decrypt(decrypter);
      return jsonWebEncryption.toJsonWebSignature();
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
