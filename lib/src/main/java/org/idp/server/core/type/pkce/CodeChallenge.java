package org.idp.server.core.type.pkce;

import java.util.Objects;

public class CodeChallenge {
  String value;

  public CodeChallenge() {}

  public CodeChallenge(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CodeChallenge that = (CodeChallenge) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
