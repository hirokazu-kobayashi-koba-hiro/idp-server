package org.idp.server.type.oidc;

import java.util.Objects;

public class IdToken {
  String value;

  public IdToken() {}

  public IdToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
