package org.idp.server.core.identity.verification.delegation;

import java.util.Objects;

public class ExternalWorkflowDelegation {

  String name;

  public ExternalWorkflowDelegation() {}

  public ExternalWorkflowDelegation(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    ExternalWorkflowDelegation that = (ExternalWorkflowDelegation) o;
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
