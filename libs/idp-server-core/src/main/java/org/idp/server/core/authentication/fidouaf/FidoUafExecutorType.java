package org.idp.server.core.authentication.fidouaf;

import java.util.Objects;

public class FidoUafExecutorType {
  String name;

  public FidoUafExecutorType() {}

  public FidoUafExecutorType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    FidoUafExecutorType that = (FidoUafExecutorType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
