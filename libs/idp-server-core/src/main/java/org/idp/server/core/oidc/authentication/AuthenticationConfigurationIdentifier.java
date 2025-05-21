package org.idp.server.core.oidc.authentication;

import java.util.Objects;

public class AuthenticationConfigurationIdentifier {

  String value;

  public AuthenticationConfigurationIdentifier() {}

  public AuthenticationConfigurationIdentifier(String value) {
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
    AuthenticationConfigurationIdentifier that = (AuthenticationConfigurationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
