package org.idp.server.basic.jose;

public class JwtContextCreator implements JoseContextCreator {
  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    JsonWebToken jsonWebToken = JsonWebToken.parse(jose);
    JsonWebTokenClaims claims = jsonWebToken.claims();
    return new JoseContext(new JsonWebSignature(), claims, new JsonWebSignatureVerifier());
  }
}
