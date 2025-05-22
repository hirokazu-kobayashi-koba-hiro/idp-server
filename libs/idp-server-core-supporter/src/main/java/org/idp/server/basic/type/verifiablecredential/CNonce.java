/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

/**
 * c_nonce OPTIONAL.
 *
 * <p>OPTIONAL. JSON string containing a nonce to be used to create a proof of possession of key
 * material when requesting a Credential (see Section 7.2). When received, the Wallet MUST use this
 * nonce value for its subsequent credential requests until the Credential Issuer provides a fresh
 * nonce.
 *
 * @see <a
 *     href="https://openid.bitbucket.io/connect/openid-4-verifiable-credential-issuance-1_0.html#name-credential-response">7.3.
 *     Credential Response</a>
 */
public class CNonce {
  String value;

  public CNonce() {}

  public CNonce(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CNonce nonce = (CNonce) o;
    return Objects.equals(value, nonce.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
