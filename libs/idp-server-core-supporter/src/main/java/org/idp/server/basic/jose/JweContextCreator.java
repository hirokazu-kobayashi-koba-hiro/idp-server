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
