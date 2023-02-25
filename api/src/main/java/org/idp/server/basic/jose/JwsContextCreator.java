package org.idp.server.basic.jose;

/** JwsContextCreator */
public class JwsContextCreator implements JoseContextCreator {
  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    try {
      JsonWebSignature jsonWebSignature = JsonWebSignature.parse(jose);
      JsonWebSignatureVerifierFactory factory = new JsonWebSignatureVerifierFactory();
      JsonWebSignatureVerifier jwsVerifier = factory.create(jsonWebSignature, publicJwks);
      JsonWebTokenClaims claims = jsonWebSignature.claims();
      return new JoseContext(jsonWebSignature, claims, jwsVerifier);
    } catch (JwkInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
