package org.idp.server.type;

import java.util.Objects;

/** IdTokenHint */
public class IdTokenHint {
  String value;

  public IdTokenHint() {}

  public IdTokenHint(String value) {
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
    IdTokenHint that = (IdTokenHint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
