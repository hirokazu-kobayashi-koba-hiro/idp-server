package org.idp.server.core.type.oidc;

import java.util.Objects;

/** Scope */
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
