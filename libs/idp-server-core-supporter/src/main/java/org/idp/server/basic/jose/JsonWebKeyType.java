/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
