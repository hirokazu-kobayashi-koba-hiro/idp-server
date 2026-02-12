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
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.util.Map;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.idp.server.platform.exception.UnSupportedException;

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
    return createJwsFromPem(claims, customHeaders, pemPrivateKey);
  }

  public JsonWebSignature createWithAsymmetricKeyForPem(
      Map<String, Object> claims, Map<String, Object> customHeaders, String pemPrivateKey)
      throws JoseInvalidException {
    return createJwsFromPem(claims, customHeaders, pemPrivateKey);
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

  private <T> JsonWebSignature createJwsFromPem(
      T claims, Map<String, Object> customHeaders, String pemPrivateKey)
      throws JoseInvalidException {
    try {
      PrivateKey privateKey = parseP8PrivateKey(pemPrivateKey);
      JWSAlgorithm algorithm = determineAlgorithm(privateKey);
      JWSSigner signer = createSigner(privateKey);

      JWSHeader header = new JWSHeader.Builder(algorithm).customParams(customHeaders).build();

      JWTClaimsSet claimsSet = parseClaimsSet(claims);
      SignedJWT signedJWT = new SignedJWT(header, claimsSet);
      signedJWT.sign(signer);

      return new JsonWebSignature(signedJWT);
    } catch (Exception e) {
      throw new JoseInvalidException("Failed to create JWS from PEM key: " + e.getMessage(), e);
    }
  }

  private JWSAlgorithm determineAlgorithm(PrivateKey privateKey) throws JoseInvalidException {
    return switch (privateKey) {
      case ECPrivateKey ignored -> JWSAlgorithm.ES256;
      case RSAPrivateKey ignored -> JWSAlgorithm.RS256;
      default ->
          throw new JoseInvalidException("Unsupported private key type: " + privateKey.getClass());
    };
  }

  private JWSSigner createSigner(PrivateKey privateKey) throws JOSEException {
    return switch (privateKey) {
      case ECPrivateKey ecPrivateKey -> new ECDSASigner(ecPrivateKey);
      case RSAPrivateKey rsaPrivateKey -> new RSASSASigner(rsaPrivateKey);
      default ->
          throw new IllegalArgumentException(
              "Unsupported private key type: " + privateKey.getClass());
    };
  }

  private <T> JWTClaimsSet parseClaimsSet(T claims) throws ParseException {
    return switch (claims) {
      case String stringClaims -> JWTClaimsSet.parse(stringClaims);
      case Map<?, ?> mapClaims -> JWTClaimsSet.parse((Map<String, Object>) mapClaims);
      default ->
          throw new IllegalArgumentException("Unsupported claims type: " + claims.getClass());
    };
  }

  /** Parse P8 private key from PEM format using BouncyCastle */
  private PrivateKey parseP8PrivateKey(String pemContent) throws Exception {
    try (PEMParser pemParser = new PEMParser(new StringReader(pemContent))) {
      Object object = pemParser.readObject();

      if (object instanceof PrivateKeyInfo privateKeyInfo) {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        return converter.getPrivateKey(privateKeyInfo);
      } else {
        throw new IllegalArgumentException("PEM content does not contain a valid private key");
      }
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
