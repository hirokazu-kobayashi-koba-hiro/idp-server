package org.idp.server.core.authentication;

import java.util.Objects;

public class MfaInteractionType {
  String name;

  public MfaInteractionType() {}

  public MfaInteractionType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    MfaInteractionType that = (MfaInteractionType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
