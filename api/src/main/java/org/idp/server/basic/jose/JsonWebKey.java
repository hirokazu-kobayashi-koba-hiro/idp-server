package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyType;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

/** JsonWebKey */
public class JsonWebKey {
  JWK value;

  public JsonWebKey() {}

  public JsonWebKey(JWK value) {
    this.value = value;
  }

  public JWK value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }

  public String keyId() {
    return value.getKeyID();
  }

  public String algorithm() {
    return value.getAlgorithm().getName();
  }

  public JsonWebKeyType keyType() {
    KeyType keyType = value.getKeyType();
    return JsonWebKeyType.of(keyType);
  }

  PublicKey toPublicKey() throws JwkInvalidException {
    JsonWebKeyType jsonWebKeyType = keyType();
    try {
      switch (jsonWebKeyType) {
        case RSA -> {
          return value.toRSAKey().toPublicKey();
        }
        case EC -> {
          return value.toECKey().toPublicKey();
        }
        case OCT -> {
          return value.toOctetKeyPair().toPublicKey();
        }
        default -> throw new JwkInvalidException("unsupported key type");
      }
    } catch (JOSEException e) {
      throw new JwkInvalidException(e.getMessage(), e);
    }
  }

  PrivateKey toPrivateKey() throws JwkInvalidException {
    JsonWebKeyType jsonWebKeyType = keyType();
    try {
      switch (jsonWebKeyType) {
        case RSA -> {
          return value.toRSAKey().toPrivateKey();
        }
        case EC -> {
          return value.toECKey().toPrivateKey();
        }
        case OCT -> {
          return value.toOctetKeyPair().toPrivateKey();
        }
        default -> throw new JwkInvalidException("unsupported key type");
      }
    } catch (JOSEException e) {
      throw new JwkInvalidException(e.getMessage(), e);
    }
  }
}
