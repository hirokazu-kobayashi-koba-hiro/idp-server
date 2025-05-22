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
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import org.idp.server.platform.exception.UnSupportedException;

public class JsonWebEncrypterFactory {

  JsonWebKey jsonWebKey;

  public JsonWebEncrypterFactory(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public JWEEncrypter create() throws JsonWebKeyInvalidException, JOSEException {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    switch (jsonWebKeyType) {
      case EC -> {
        return new ECDHEncrypter((ECPublicKey) jsonWebKey.toPublicKey());
      }
      case RSA -> {
        return new RSAEncrypter((RSAPublicKey) jsonWebKey.toPublicKey());
      }
      default -> {
        throw new UnSupportedException(
            String.format("unsupported encryption alg (%s)", jsonWebKeyType.name()));
      }
    }
  }
}
