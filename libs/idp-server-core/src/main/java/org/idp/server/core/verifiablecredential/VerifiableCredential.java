package org.idp.server.core.verifiablecredential;

import java.util.Objects;

public class VerifiableCredential {
  Object value;

  public VerifiableCredential() {}

  public VerifiableCredential(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
