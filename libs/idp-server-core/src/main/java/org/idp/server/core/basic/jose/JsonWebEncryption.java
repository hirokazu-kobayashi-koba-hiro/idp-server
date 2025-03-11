package org.idp.server.core.basic.jose;

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
