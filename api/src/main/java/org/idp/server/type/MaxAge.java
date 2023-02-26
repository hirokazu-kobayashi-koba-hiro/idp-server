package org.idp.server.type;

import java.util.Objects;

/** MaxAge */
public class MaxAge {
  Long value;

  public MaxAge() {}

  public MaxAge(long value) {
    this.value = value;
  }

  public long value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
