package org.idp.server.basic.jose;

/** JoseContext */
public class JoseContext {

  JsonWebSignature jsonWebSignature = new JsonWebSignature();
  JsonWebTokenClaims claims = new JsonWebTokenClaims();
  JsonWebSignatureVerifier jwsVerifier = new JsonWebSignatureVerifier();

  public JoseContext() {}

  public JoseContext(
      JsonWebSignature jsonWebSignature,
      JsonWebTokenClaims claims,
      JsonWebSignatureVerifier jwsVerifier) {
    this.jsonWebSignature = jsonWebSignature;
    this.claims = claims;
    this.jwsVerifier = jwsVerifier;
  }

  public JsonWebSignature jsonWebSignature() {
    return jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return claims;
  }

  public JsonWebSignatureVerifier jwsVerifier() {
    return jwsVerifier;
  }

  public void verifySignature() throws JoseInvalidException {
    if (hasJsonWebSignature()) {
      jwsVerifier.verify(jsonWebSignature);
    }
  }

  public boolean hasJsonWebSignature() {
    return jsonWebSignature.exists();
  }
}
