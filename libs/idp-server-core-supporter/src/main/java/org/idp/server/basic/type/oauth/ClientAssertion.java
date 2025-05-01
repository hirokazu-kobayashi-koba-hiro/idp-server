package org.idp.server.basic.type.oauth;

import java.util.Objects;

public class ClientAssertion {
  String value;

  public ClientAssertion() {}

  public ClientAssertion(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
