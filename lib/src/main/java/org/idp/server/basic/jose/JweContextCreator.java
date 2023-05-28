package org.idp.server.basic.jose;

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
      JsonWebSignatureVerifier jwsVerifier = factory.create();
      JsonWebTokenClaims claims = jsonWebSignature.claims();
      return new JoseContext(jsonWebSignature, claims, jwsVerifier);
    } catch (JwkInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
