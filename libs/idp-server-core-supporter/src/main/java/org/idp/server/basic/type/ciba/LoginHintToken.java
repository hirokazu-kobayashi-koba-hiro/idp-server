package org.idp.server.basic.type.ciba;

import java.util.Objects;

public class LoginHintToken {
  String value;

  public LoginHintToken() {}

  public LoginHintToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    LoginHintToken that = (LoginHintToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
