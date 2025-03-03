package org.idp.server.core.oauth.request;

import java.util.Objects;

/** AuthorizationRequestIdentifier */
public class AuthorizationRequestIdentifier {
  String value;

  public AuthorizationRequestIdentifier() {}

  public AuthorizationRequestIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuthorizationRequestIdentifier that = (AuthorizationRequestIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
