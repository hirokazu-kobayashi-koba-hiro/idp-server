package org.idp.server.core.type;

import java.util.Objects;

/** Scope */
public class Scope {
  String value;

  public Scope() {}

  public Scope(String value) {
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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Scope scope = (Scope) o;
    return Objects.equals(value, scope.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
