/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

import org.idp.server.basic.type.extension.Pairs;

public class JweContextCreator implements JoseContextCreator {

  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    try {
      JsonWebEncryption jsonWebEncryption = JsonWebEncryption.parse(jose);
      JsonWebEncDecrypterFactory jsonWebEncDecrypterFactory =
          new JsonWebEncDecrypterFactory(jsonWebEncryption, privateJwks, secret);
      JsonWebEncryptionDecrypter jsonWebEncryptionDecrypter = jsonWebEncDecrypterFactory.create();
      JsonWebSignature jsonWebSignature = jsonWebEncryptionDecrypter.decrypt(jsonWebEncryption);
      JsonWebSignatureVerifierFactory factory =
          new JsonWebSignatureVerifierFactory(jsonWebSignature, publicJwks, secret);
      Pairs<JsonWebSignatureVerifier, JsonWebKey> pairs = factory.create();
      JsonWebTokenClaims claims = jsonWebSignature.claims();
      return new JoseContext(jsonWebSignature, claims, pairs.getLeft(), pairs.getRight());
    } catch (JsonWebKeyInvalidException | JsonWebKeyNotFoundException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
