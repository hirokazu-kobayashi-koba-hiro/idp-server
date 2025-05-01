package org.idp.server.basic.dependency.protocol;

import java.util.Objects;

public class AuthorizationProtocolProvider {

  String name;

  public AuthorizationProtocolProvider() {}

  public AuthorizationProtocolProvider(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthorizationProtocolProvider that = (AuthorizationProtocolProvider) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
