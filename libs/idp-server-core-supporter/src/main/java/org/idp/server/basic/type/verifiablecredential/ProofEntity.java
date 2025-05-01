package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

public class ProofEntity {
  Object value;

  public ProofEntity() {}

  public ProofEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
