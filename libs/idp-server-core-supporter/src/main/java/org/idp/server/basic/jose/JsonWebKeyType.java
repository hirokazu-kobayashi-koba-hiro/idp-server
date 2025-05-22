/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

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
