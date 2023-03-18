package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;

public class JsonWebSignatureVerifierFactory {

  DefaultJWSVerifierFactory defaultJWSVerifierFactory;

  public JsonWebSignatureVerifierFactory() {
    this.defaultJWSVerifierFactory = new DefaultJWSVerifierFactory();
  }

  public JsonWebSignatureVerifier create(JsonWebSignature jsonWebSignature, String publicJwks)
      throws JwkInvalidException, JoseInvalidException {
    try {
      String keyId = jsonWebSignature.keyId();
      JsonWebKeys publicKeys = JwkParser.parseKeys(publicJwks);
      JsonWebKey publicKey = publicKeys.find(keyId);
      SignedJWT signedJWT = jsonWebSignature.value();
      JWSVerifier jwsVerifier =
          defaultJWSVerifierFactory.createJWSVerifier(
              signedJWT.getHeader(), publicKey.toPublicKey());
      return new JsonWebSignatureVerifier(jwsVerifier);
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
