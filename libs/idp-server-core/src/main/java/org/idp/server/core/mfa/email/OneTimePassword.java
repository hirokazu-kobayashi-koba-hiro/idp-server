package org.idp.server.core.mfa.email;

import java.util.Objects;

public class OneTimePassword {
  String value;

  OneTimePassword() {}

  OneTimePassword(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
