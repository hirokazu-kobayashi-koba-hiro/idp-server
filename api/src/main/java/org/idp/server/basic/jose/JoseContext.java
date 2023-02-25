package org.idp.server.basic.jose;

/** JoseContext */
public class JoseContext {

  JsonWebSignature jsonWebSignature;
  JsonWebTokenClaims claims;
  JsonWebSignatureVerifier jwsVerifier;

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
    jwsVerifier.verify(jsonWebSignature);
  }
}
