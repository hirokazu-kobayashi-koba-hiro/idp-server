package org.idp.server.core.verifiable_credential;

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
