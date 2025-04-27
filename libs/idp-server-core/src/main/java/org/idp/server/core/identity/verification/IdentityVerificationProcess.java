package org.idp.server.core.identity.verification;

import java.util.Objects;

public class IdentityVerificationProcess {
  String name;

  public IdentityVerificationProcess() {}

  public IdentityVerificationProcess(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    IdentityVerificationProcess that = (IdentityVerificationProcess) o;
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
