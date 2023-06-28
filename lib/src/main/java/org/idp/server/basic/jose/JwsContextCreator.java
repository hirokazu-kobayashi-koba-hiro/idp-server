package org.idp.server.basic.jose;

import org.idp.server.type.extension.Pairs;

/** JwsContextCreator */
public class JwsContextCreator implements JoseContextCreator {
  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    try {
      JsonWebSignature jsonWebSignature = JsonWebSignature.parse(jose);
      JsonWebSignatureVerifierFactory factory =
          new JsonWebSignatureVerifierFactory(jsonWebSignature, publicJwks, secret);
      Pairs<JsonWebSignatureVerifier, JsonWebKey> pairs = factory.create();
      JsonWebTokenClaims claims = jsonWebSignature.claims();
      return new JoseContext(jsonWebSignature, claims, pairs.getLeft(), pairs.getRight());
    } catch (JsonWebKeyInvalidException | JsonWebKeyNotFoundException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
