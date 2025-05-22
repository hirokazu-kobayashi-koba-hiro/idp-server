/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oidc;

import java.util.Objects;

/**
 * nonce OPTIONAL.
 *
 * <p>String value used to associate a Client session with an ID Token, and to mitigate replay
 * attacks. The value is passed through unmodified from the Authentication Request to the ID Token.
 * Sufficient entropy MUST be present in the nonce values used to prevent attackers from guessing
 * values. For implementation notes, see Section 15.5.2.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class Nonce {
  String value;

  public Nonce() {}

  public Nonce(String value) {
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
    Nonce nonce = (Nonce) o;
    return Objects.equals(value, nonce.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
