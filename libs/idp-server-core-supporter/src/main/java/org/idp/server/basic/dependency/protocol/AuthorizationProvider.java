package org.idp.server.basic.dependency.protocol;

import java.util.Objects;

public class AuthorizationProvider {

  String name;

  public AuthorizationProvider() {}

  public AuthorizationProvider(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthorizationProvider that = (AuthorizationProvider) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
