package org.idp.server.core.type;

import java.util.Objects;

public class AccessToken {
  String value;

  public AccessToken() {}

  public AccessToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AccessToken that = (AccessToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
