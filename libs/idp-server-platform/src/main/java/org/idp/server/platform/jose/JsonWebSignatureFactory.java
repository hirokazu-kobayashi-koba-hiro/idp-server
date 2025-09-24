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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

// FIXME refactor
public class JsonWebSignatureFactory {

  public JsonWebSignatureFactory() {}

  public JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, String jwks, String keyId)
      throws JsonWebKeyInvalidException, JoseInvalidException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
    JsonWebKey jsonWebKey = jsonWebKeys.findBy(keyId);
    return this.createWithAsymmetricKey(claims, customHeaders, jsonWebKey);
  }

  public JsonWebSignature createWithAsymmetricKeyByAlgorithm(
      Map<String, Object> claims, Map<String, Object> customHeaders, String jwks, String algorithm)
      throws JsonWebKeyInvalidException, JoseInvalidException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
    JsonWebKey jsonWebKey = jsonWebKeys.findByAlgorithm(algorithm);
    return this.createWithAsymmetricKey(claims, customHeaders, jsonWebKey);
  }

  public JsonWebSignature createWithAsymmetricKey(
      String claims, Map<String, Object> customHeaders, String jwks, String keyId)
      throws JsonWebKeyInvalidException, JoseInvalidException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
    JsonWebKey jsonWebKey = jsonWebKeys.findBy(keyId);
    return this.createWithAsymmetricKey(claims, customHeaders, jsonWebKey);
  }

  public JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, String privateKey)
      throws JsonWebKeyInvalidException {
    try {
      JsonWebKey jsonWebKey = JwkParser.parse(privateKey);
      JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (JOSEException | ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public JsonWebSignature createWithAsymmetricKey(
      String claims, Map<String, Object> customHeaders, String privateKey)
      throws JoseInvalidException {
    try {
      JsonWebKey jsonWebKey = JwkParser.parse(privateKey);
      JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (JsonWebKeyInvalidException | JOSEException | ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public JsonWebSignature createWithAsymmetricKeyForPem(
      String claims, Map<String, Object> customHeaders, String pemPrivateKey)
      throws JoseInvalidException {
    try {
      JWK jwk = JWK.parseFromPEMEncodedObjects(pemPrivateKey);
      JsonWebKey jsonWebKey = new JsonWebKey(jwk);

      // Determine algorithm based on key type if not already set
      JWSAlgorithm jwsAlgorithm;
      if (jsonWebKey.algorithm() != null) {
        jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      } else {
        // Default algorithms based on key type
        JsonWebKeyType keyType = jsonWebKey.keyType();
        switch (keyType) {
          case EC -> jwsAlgorithm = JWSAlgorithm.ES256;
          case RSA -> jwsAlgorithm = JWSAlgorithm.RS256;
          default -> throw new JoseInvalidException("Unsupported key type: " + keyType);
        }
      }

      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (Exception e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public JsonWebSignature createWithAsymmetricKeyForPem(
      Map<String, Object> claims, Map<String, Object> customHeaders, String pemPrivateKey)
      throws JoseInvalidException {
    try {
      JWK jwk = JWK.parseFromPEMEncodedObjects(pemPrivateKey);
      JsonWebKey jsonWebKey = new JsonWebKey(jwk);

      // Determine algorithm based on key type if not already set
      JWSAlgorithm jwsAlgorithm;
      if (jsonWebKey.algorithm() != null) {
        jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      } else {
        // Default algorithms based on key type
        JsonWebKeyType keyType = jsonWebKey.keyType();
        switch (keyType) {
          case EC -> jwsAlgorithm = JWSAlgorithm.ES256;
          case RSA -> jwsAlgorithm = JWSAlgorithm.RS256;
          default -> throw new JoseInvalidException("Unsupported key type: " + keyType);
        }
      }

      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (Exception e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, JsonWebKey jsonWebKey)
      throws JoseInvalidException {
    try {
      JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (JsonWebKeyInvalidException | JOSEException | ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  JsonWebSignature createWithAsymmetricKey(
      String claims, Map<String, Object> customHeaders, JsonWebKey jsonWebKey)
      throws JoseInvalidException {
    try {
      JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jsonWebKey.algorithm());
      JWSHeader jwsHeader =
          new JWSHeader.Builder(jwsAlgorithm)
              .keyID(jsonWebKey.keyId())
              .customParams(customHeaders)
              .build();

      JWTClaimsSet claimsSet = JWTClaimsSet.parse(claims);
      SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
      JWSSigner jwsSigner = of(jsonWebKey);
      signedJWT.sign(jwsSigner);
      return new JsonWebSignature(signedJWT);
    } catch (JsonWebKeyInvalidException | JOSEException | ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  JWSSigner of(JsonWebKey jsonWebKey) throws JsonWebKeyInvalidException, JOSEException {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    PrivateKey privateKey = jsonWebKey.toPrivateKey();
    switch (jsonWebKeyType) {
      case EC -> {
        return new ECDSASigner((ECPrivateKey) privateKey);
      }
      case RSA -> {
        return new RSASSASigner(privateKey);
      }
      default -> {
        throw new UnSupportedException(
            String.format("unsupported sign key (%s)", jsonWebKeyType.name()));
      }
    }
  }
}
