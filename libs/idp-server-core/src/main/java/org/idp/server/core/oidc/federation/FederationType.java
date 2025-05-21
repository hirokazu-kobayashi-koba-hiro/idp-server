package org.idp.server.core.oidc.federation;

import java.util.Objects;

public class FederationType {
  String name;

  public FederationType() {}

  public FederationType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    FederationType federationType = (FederationType) o;
    return Objects.equals(name, federationType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
