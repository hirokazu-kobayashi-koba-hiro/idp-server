package org.idp.server.platform.security.event;

import java.util.Objects;

public class SecurityEventIdentifier {
  String value;

  public SecurityEventIdentifier() {}

  public SecurityEventIdentifier(String value) {
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
    if (o == null || getClass() != o.getClass()) return false;
    SecurityEventIdentifier that = (SecurityEventIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
