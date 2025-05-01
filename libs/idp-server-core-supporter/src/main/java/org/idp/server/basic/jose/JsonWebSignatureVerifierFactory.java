package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;
import org.idp.server.basic.type.extension.Pairs;

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

  public Pairs<JsonWebSignatureVerifier, JsonWebKey> create()
      throws JsonWebKeyInvalidException, JoseInvalidException, JsonWebKeyNotFoundException {
    try {
      if (jsonWebSignature.isSymmetricType()) {
        MACVerifier macVerifier = new MACVerifier(secret);
        return Pairs.of(new JsonWebSignatureVerifier(macVerifier), new JsonWebKey());
      }
      JsonWebKey publicKey = get();
      SignedJWT signedJWT = jsonWebSignature.value();
      JWSVerifier jwsVerifier =
          defaultJWSVerifierFactory.createJWSVerifier(
              signedJWT.getHeader(), publicKey.toPublicKey());
      return Pairs.of(new JsonWebSignatureVerifier(jwsVerifier), publicKey);
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  private JsonWebKey get() throws JsonWebKeyInvalidException, JsonWebKeyNotFoundException {
    String keyId = jsonWebSignature.keyId();
    JsonWebKeys publicKeys = JwkParser.parseKeys(publicJwks);
    if (jsonWebSignature.hasKeyId()) {
      JsonWebKey jsonWebKey = publicKeys.findBy(keyId);
      if (jsonWebKey.exists()) {
        return jsonWebKey;
      }
    }
    String algorithm = jsonWebSignature.algorithm();
    JsonWebKey jsonWebKey = publicKeys.findByAlgorithm(algorithm);
    if (!jsonWebKey.exists()) {
      throw new JsonWebKeyNotFoundException(
          String.format("not found jwk kid (%s) algorithm (%s)", keyId, algorithm));
    }
    return jsonWebKey;
  }
}
