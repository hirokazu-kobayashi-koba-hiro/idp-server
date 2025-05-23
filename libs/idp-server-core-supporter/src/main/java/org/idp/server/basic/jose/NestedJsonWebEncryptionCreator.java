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

import com.nimbusds.jose.*;

public class NestedJsonWebEncryptionCreator {

  JsonWebSignature jsonWebSignature;
  String jweAlgorithm;
  String encryptionMethod;
  String publicKeys;

  public NestedJsonWebEncryptionCreator(
      JsonWebSignature jsonWebSignature,
      String jweAlgorithm,
      String encryptionMethod,
      String publicKeys) {
    this.jsonWebSignature = jsonWebSignature;
    this.jweAlgorithm = jweAlgorithm;
    this.encryptionMethod = encryptionMethod;
    this.publicKeys = publicKeys;
  }

  public String create() throws JoseInvalidException {
    try {
      JWEAlgorithm algorithm = JWEAlgorithm.parse(jweAlgorithm);
      EncryptionMethod method = EncryptionMethod.parse(encryptionMethod);
      JsonWebKeys jsonWebKeys = JwkParser.parseKeys(publicKeys);
      JsonWebKey jsonWebKey = jsonWebKeys.findByAlgorithm(jweAlgorithm);
      JWEEncrypter jweEncrypter = new JsonWebEncrypterFactory(jsonWebKey).create();
      JWEObject jweObject =
          new JWEObject(
              new JWEHeader.Builder(algorithm, method).contentType("JWT").build(),
              new Payload(jsonWebSignature.value()));
      jweObject.encrypt(jweEncrypter);
      return jweObject.serialize();
    } catch (JsonWebKeyInvalidException | JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
