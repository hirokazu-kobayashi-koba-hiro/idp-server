package org.idp.server.core.token;

import java.util.Objects;

public class OAuthTokenIdentifier {
  String value;

  public OAuthTokenIdentifier() {}

  public OAuthTokenIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
