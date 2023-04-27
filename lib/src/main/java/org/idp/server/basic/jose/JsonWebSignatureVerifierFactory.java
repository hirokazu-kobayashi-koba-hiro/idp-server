package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;

public class JsonWebSignatureVerifierFactory {

  DefaultJWSVerifierFactory defaultJWSVerifierFactory;

  public JsonWebSignatureVerifierFactory() {
    this.defaultJWSVerifierFactory = new DefaultJWSVerifierFactory();
  }

  public JsonWebSignatureVerifier create(
      JsonWebSignature jsonWebSignature, String publicJwks, String secret)
      throws JwkInvalidException, JoseInvalidException {
    try {
      if (jsonWebSignature.isSymmetricType()) {
        MACVerifier macVerifier = new MACVerifier(secret);
        return new JsonWebSignatureVerifier(macVerifier);
      }
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
