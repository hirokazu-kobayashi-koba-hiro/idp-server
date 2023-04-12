package org.idp.server.type.oauth;

import java.util.Objects;

public class AuthorizationHeader {
  String value;

  public AuthorizationHeader() {}

  public AuthorizationHeader(String value) {
    this.value = value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public boolean isBasic() {
    return true;
  }

  public boolean isBearer() {
    return true;
  }
}
