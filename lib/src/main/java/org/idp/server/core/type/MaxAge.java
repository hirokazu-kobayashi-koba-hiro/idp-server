package org.idp.server.core.type;

import java.util.Objects;

/** MaxAge */
public class MaxAge {
  String value;

  public MaxAge() {}

  public MaxAge(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public long toLongValue() {
    return Long.parseLong(value);
  }
}
