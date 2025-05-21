package org.idp.server.core.oidc.token;

import java.util.Objects;

public class AuthorizationHeader {
  String value;

  public AuthorizationHeader() {}

  public AuthorizationHeader(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
