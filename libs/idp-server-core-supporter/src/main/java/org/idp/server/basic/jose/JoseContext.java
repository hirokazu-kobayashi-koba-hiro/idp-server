package org.idp.server.basic.jose;

import java.util.Map;

/** JoseContext */
public class JoseContext {

  JsonWebSignature jsonWebSignature = new JsonWebSignature();
  JsonWebTokenClaims claims = new JsonWebTokenClaims();
  JsonWebSignatureVerifier jwsVerifier = new JsonWebSignatureVerifier();
  JsonWebKey jsonWebKey = new JsonWebKey();

  public JoseContext() {}

  public JoseContext(JsonWebSignature jsonWebSignature, JsonWebTokenClaims claims, JsonWebSignatureVerifier jwsVerifier, JsonWebKey jsonWebKey) {
    this.jsonWebSignature = jsonWebSignature;
    this.claims = claims;
    this.jwsVerifier = jwsVerifier;
    this.jsonWebKey = jsonWebKey;
  }

  public JsonWebSignature jsonWebSignature() {
    return jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return claims;
  }

  public Map<String, Object> claimsAsMap() {
    return claims.toMap();
  }

  public JsonWebSignatureVerifier jwsVerifier() {
    return jwsVerifier;
  }

  public JsonWebKey jsonWebKey() {
    return jsonWebKey;
  }

  public void verifySignature() throws JoseInvalidException {
    if (hasJsonWebSignature()) {
      jwsVerifier.verify(jsonWebSignature);
    }
  }

  public boolean hasJsonWebSignature() {
    return jsonWebSignature.exists();
  }

  public boolean exists() {
    return claims.exists();
  }

  public boolean isSymmetricKey() {
    return jsonWebSignature.isSymmetricType();
  }
}
