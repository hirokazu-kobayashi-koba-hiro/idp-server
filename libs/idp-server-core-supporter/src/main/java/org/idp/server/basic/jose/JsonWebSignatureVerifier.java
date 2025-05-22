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
