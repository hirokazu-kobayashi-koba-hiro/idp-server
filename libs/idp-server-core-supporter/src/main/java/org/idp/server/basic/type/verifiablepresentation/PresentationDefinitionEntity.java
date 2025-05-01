package org.idp.server.basic.type.verifiablepresentation;

import java.util.Objects;

public class PresentationDefinitionEntity {
  Object value;

  public PresentationDefinitionEntity() {}

  public PresentationDefinitionEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
