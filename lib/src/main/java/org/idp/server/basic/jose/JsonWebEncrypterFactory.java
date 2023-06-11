package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

public class JsonWebEncrypterFactory {

  JsonWebKey jsonWebKey;

  public JsonWebEncrypterFactory(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public JWEEncrypter create() throws JwkInvalidException, JOSEException {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    switch (jsonWebKeyType) {
      case EC -> {
        return new ECDHEncrypter((ECPublicKey) jsonWebKey.toPublicKey());
      }
      case RSA -> {
        return new RSAEncrypter((RSAPublicKey) jsonWebKey.toPublicKey());
      }
      default -> {
        throw new RuntimeException("unsupported encryption alg");
      }
    }
  }
}
