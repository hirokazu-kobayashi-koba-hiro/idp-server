package org.idp.server.basic.jose;

import org.idp.server.basic.type.extension.Pairs;

public class JweContextCreator implements JoseContextCreator {

  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    try {
      JsonWebEncryption jsonWebEncryption = JsonWebEncryption.parse(jose);
      JsonWebEncDecrypterFactory jsonWebEncDecrypterFactory =
          new JsonWebEncDecrypterFactory(jsonWebEncryption, privateJwks, secret);
      JsonWebEncryptionDecrypter jsonWebEncryptionDecrypter = jsonWebEncDecrypterFactory.create();
      JsonWebSignature jsonWebSignature = jsonWebEncryptionDecrypter.decrypt(jsonWebEncryption);
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
