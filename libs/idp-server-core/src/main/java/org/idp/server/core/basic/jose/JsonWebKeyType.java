package org.idp.server.core.basic.jose;

import com.nimbusds.jose.jwk.KeyType;

/** KeyType */
public enum JsonWebKeyType {
  RSA,
  EC,
  OCT,
  OKP,
  UNKNOWN;

  public static JsonWebKeyType of(KeyType keyType) {
    String keyTypeValue = keyType.getValue();
    if ("EC".equalsIgnoreCase(keyTypeValue)) {
      return EC;
    }
    if ("RSA".equalsIgnoreCase(keyTypeValue)) {
      return RSA;
    }
    if ("OCT".equalsIgnoreCase(keyTypeValue)) {
      return OCT;
    }
    if ("OKP".equalsIgnoreCase(keyTypeValue)) {
      return OKP;
    }
    return UNKNOWN;
  }

  public boolean isRsa() {
    return this == RSA;
  }

  public boolean isEc() {
    return this == EC;
  }
}
