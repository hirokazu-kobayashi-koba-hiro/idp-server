package org.idp.server.core.type.verifiablecredential;

import java.util.Objects;

public class CredentialDefinitionEntity {
  Object value;

  public CredentialDefinitionEntity() {}

  public CredentialDefinitionEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
