package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;

public class JsonWebSignatureVerifierFactory {

  JsonWebSignature jsonWebSignature;
  String publicJwks;
  String secret;
  DefaultJWSVerifierFactory defaultJWSVerifierFactory;

  public JsonWebSignatureVerifierFactory(
      JsonWebSignature jsonWebSignature, String publicJwks, String secret) {
    this.jsonWebSignature = jsonWebSignature;
    this.publicJwks = publicJwks;
    this.secret = secret;
    this.defaultJWSVerifierFactory = new DefaultJWSVerifierFactory();
  }

  public JsonWebSignatureVerifier create() throws JwkInvalidException, JoseInvalidException {
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
