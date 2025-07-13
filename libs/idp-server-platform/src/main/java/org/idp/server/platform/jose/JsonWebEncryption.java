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

package org.idp.server.platform.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.util.Objects;

public class JsonWebEncryption {
  EncryptedJWT value;

  public JsonWebEncryption() {}

  public JsonWebEncryption(EncryptedJWT value) {
    this.value = value;
  }

  public static JsonWebEncryption parse(String jose) throws JoseInvalidException {
    try {
      EncryptedJWT encryptedJWT = EncryptedJWT.parse(jose);
      return new JsonWebEncryption(encryptedJWT);
    } catch (ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public String serialize() {
    return value.serialize();
  }

  EncryptedJWT value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }

  public String keyId() {
    return value.getHeader().getKeyID();
  }

  public JsonWebTokenClaims claims() {
    try {
      JWTClaimsSet jwtClaimsSet = value.getJWTClaimsSet();
      return new JsonWebTokenClaims(jwtClaimsSet);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  void decrypt(JWEDecrypter decrypter) throws JOSEException {
    value.decrypt(decrypter);
  }

  JsonWebSignature toJsonWebSignature() {
    return new JsonWebSignature(value.getPayload().toSignedJWT());
  }
}
