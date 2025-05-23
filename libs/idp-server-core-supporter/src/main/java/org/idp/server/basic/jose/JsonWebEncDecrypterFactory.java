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
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jwt.EncryptedJWT;

public class JsonWebEncDecrypterFactory {

  JsonWebEncryption jsonWebEncryption;
  String privateJwks;
  String secret;
  DefaultJWEDecrypterFactory defaultJWEDecrypterFactory;

  public JsonWebEncDecrypterFactory(
      JsonWebEncryption jsonWebEncryption, String privateJwks, String secret) {
    this.jsonWebEncryption = jsonWebEncryption;
    this.privateJwks = privateJwks;
    this.secret = secret;
    this.defaultJWEDecrypterFactory = new DefaultJWEDecrypterFactory();
  }

  public JsonWebEncryptionDecrypter create()
      throws JsonWebKeyInvalidException, JoseInvalidException {
    try {

      String keyId = jsonWebEncryption.keyId();
      JsonWebKeys publicKeys = JwkParser.parseKeys(privateJwks);
      JsonWebKey privateKey = publicKeys.findBy(keyId);
      EncryptedJWT encryptedJWT = jsonWebEncryption.value();
      JWEDecrypter decrypter =
          defaultJWEDecrypterFactory.createJWEDecrypter(
              encryptedJWT.getHeader(), privateKey.toPrivateKey());
      return new JsonWebEncryptionDecrypter(decrypter);
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
