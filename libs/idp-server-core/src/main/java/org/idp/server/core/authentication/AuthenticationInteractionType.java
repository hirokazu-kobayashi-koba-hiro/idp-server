package org.idp.server.core.authentication;

import java.util.Objects;

public class AuthenticationInteractionType {
  String name;

  public AuthenticationInteractionType() {}

  public AuthenticationInteractionType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    AuthenticationInteractionType that = (AuthenticationInteractionType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
