package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Objects;

/** JsonWebSignature */
public class JsonWebSignature {
  SignedJWT value;

  public JsonWebSignature() {}

  public JsonWebSignature(SignedJWT value) {
    this.value = value;
  }

  public static JsonWebSignature parse(String jose) throws JoseInvalidException {
    try {
      SignedJWT signedJWT = SignedJWT.parse(jose);
      return new JsonWebSignature(signedJWT);
    } catch (ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public String serialize() {
    return value.serialize();
  }

  SignedJWT value() {
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

  public boolean verify(JWSVerifier verifier) throws JoseInvalidException {
    try {
      return value.verify(verifier);
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public boolean isSymmetricType() {
    JWSAlgorithm algorithm = value.getHeader().getAlgorithm();
    return algorithm.equals(JWSAlgorithm.HS256)
        || algorithm.equals(JWSAlgorithm.HS384)
        || algorithm.equals(JWSAlgorithm.HS512);
  }
}
