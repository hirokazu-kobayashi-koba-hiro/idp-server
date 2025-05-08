package org.idp.server.core.oidc.configuration.authentication;

import java.util.Objects;

public class AuthenticationPolicyIdentifier {
  String value;

  public AuthenticationPolicyIdentifier() {}

  public AuthenticationPolicyIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthenticationPolicyIdentifier that = (AuthenticationPolicyIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
