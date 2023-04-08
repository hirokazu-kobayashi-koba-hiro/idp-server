package org.idp.server.core.type.oauth;

import java.util.Objects;

public class Subject {
  String value;

  public Subject() {}

  public Subject(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
