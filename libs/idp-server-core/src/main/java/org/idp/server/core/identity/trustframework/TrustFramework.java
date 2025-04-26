package org.idp.server.core.identity.trustframework;

import java.util.Objects;

public class TrustFramework {
  String name;

  public TrustFramework() {}

  public TrustFramework(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    TrustFramework that = (TrustFramework) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }
}
