package org.idp.server.core.type.oauth;

import java.util.Objects;

public class Username {
  String value;

  public Username() {}

  public Username(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
