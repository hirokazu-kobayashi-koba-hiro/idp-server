package org.idp.server.core.federation;

import java.util.Objects;

public class SsoProvider {
  String name;

  public SsoProvider() {}

  public SsoProvider(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    SsoProvider federationType = (SsoProvider) o;
    return Objects.equals(name, federationType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
