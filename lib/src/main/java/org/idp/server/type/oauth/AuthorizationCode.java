package org.idp.server.type.oauth;

import java.util.Objects;

/**
 * AuthorizationCode
 *
 * <p>authorization grant
 */
public class AuthorizationCode {
  String value;

  public AuthorizationCode() {}

  public AuthorizationCode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuthorizationCode that = (AuthorizationCode) o;
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
