package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jwt.EncryptedJWT;

public class JsonWebEncDecrypterFactory {

  JsonWebEncryption jsonWebEncryption;
  String privateJwks;
  String secret;
  DefaultJWEDecrypterFactory defaultJWEDecrypterFactory;

  public JsonWebEncDecrypterFactory(
      JsonWebEncryption jsonWebEncryption, String privateJwks, String secret) {
    this.jsonWebEncryption = jsonWebEncryption;
    this.privateJwks = privateJwks;
    this.secret = secret;
    this.defaultJWEDecrypterFactory = new DefaultJWEDecrypterFactory();
  }

  public JsonWebEncryptionDecrypter create() throws JwkInvalidException, JoseInvalidException {
    try {

      String keyId = jsonWebEncryption.keyId();
      JsonWebKeys publicKeys = JwkParser.parseKeys(privateJwks);
      JsonWebKey privateKey = publicKeys.findBy(keyId);
      EncryptedJWT encryptedJWT = jsonWebEncryption.value();
      JWEDecrypter decrypter =
          defaultJWEDecrypterFactory.createJWEDecrypter(
              encryptedJWT.getHeader(), privateKey.toPrivateKey());
      return new JsonWebEncryptionDecrypter(decrypter);
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}
