package org.idp.server.core.identity.trustframework;

import java.util.Objects;

public class VerificationProcess {
  String name;

  public VerificationProcess() {}

  public VerificationProcess(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    VerificationProcess that = (VerificationProcess) o;
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
