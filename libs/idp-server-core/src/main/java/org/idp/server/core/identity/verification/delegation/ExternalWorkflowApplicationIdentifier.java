package org.idp.server.core.identity.verification.delegation;

import java.util.Objects;

public class ExternalWorkflowApplicationIdentifier {

  String value;

  public ExternalWorkflowApplicationIdentifier() {}

  public ExternalWorkflowApplicationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ExternalWorkflowApplicationIdentifier that = (ExternalWorkflowApplicationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
