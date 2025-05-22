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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import java.security.PublicKey;

/** JsonWebSignatureVerifier */
public class JsonWebSignatureVerifier {

  JWSVerifier verifier;

  JsonWebSignatureVerifier() {}

  JsonWebSignatureVerifier(JWSVerifier verifier) {
    this.verifier = verifier;
  }

  public JsonWebSignatureVerifier(
      JsonWebSignatureHeader jsonWebSignatureHeader, PublicKey publicKey)
      throws JoseInvalidException {
    try {
      DefaultJWSVerifierFactory defaultJWSVerifierFactory = new DefaultJWSVerifierFactory();
      this.verifier =
          defaultJWSVerifierFactory.createJWSVerifier(jsonWebSignatureHeader.jwsHeader, publicKey);
    } catch (JOSEException exception) {
      throw new JoseInvalidException(
          "failed create JsonWebSignatureVerifier ,invalid json web signature header and public key",
          exception);
    }
  }

  public void verify(JsonWebSignature jsonWebSignature) throws JoseInvalidException {
    boolean verified = jsonWebSignature.verify(verifier);
    if (!verified) {
      throw new JoseInvalidException("invalid signature");
    }
  }
}
