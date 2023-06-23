package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;
import java.util.Map;

/** JsonWebSignatureFactory */
public class JsonWebSignatureFactory {

  public JsonWebSignatureFactory() {}

  public JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, String jwks, String keyId)
      throws JwkInvalidException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
    JsonWebKey jsonWebKey = jsonWebKeys.find(keyId);
    return this.createWithAsymmetricKey(claims, customHeaders, jsonWebKey);
  }

  public JsonWebSignature createWithAsymmetricKey(
      String claims, Map<String, Object> customHeaders, String jwks, String keyId)
      throws JwkInvalidException {
    JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
    JsonWebKey jsonWebKey = jsonWebKeys.find(keyId);
    return this.createWithAsymmetricKey(claims, customHeaders, jsonWebKey);
  }

  public JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, String privateKey)
      throws JwkInvalidException {
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
      throws JwkInvalidException {
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

  JsonWebSignature createWithAsymmetricKey(
      Map<String, Object> claims, Map<String, Object> customHeaders, JsonWebKey jsonWebKey) {
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
    } catch (JwkInvalidException | JOSEException | ParseException e) {
      throw new RuntimeException(e);
    }
  }

  JsonWebSignature createWithAsymmetricKey(
      String claims, Map<String, Object> customHeaders, JsonWebKey jsonWebKey) {
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
    } catch (JwkInvalidException | JOSEException | ParseException e) {
      throw new RuntimeException(e);
    }
  }

  JWSSigner of(JsonWebKey jsonWebKey) throws JwkInvalidException, JOSEException {
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
        throw new RuntimeException("unsupported sign key");
      }
    }
  }
}
