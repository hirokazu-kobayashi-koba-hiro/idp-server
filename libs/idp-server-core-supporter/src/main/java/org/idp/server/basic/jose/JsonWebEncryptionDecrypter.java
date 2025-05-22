/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
