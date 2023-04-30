package org.idp.server.type.oauth;

import java.util.Objects;

public class RefreshTokenValue {
  String value;

  public RefreshTokenValue() {}

  public RefreshTokenValue(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RefreshTokenValue that = (RefreshTokenValue) o;
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
