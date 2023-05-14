package org.idp.server.type.oauth;

import java.util.Objects;

public class Password {
  String value;

  public Password() {}

  public Password(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
