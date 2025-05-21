package org.idp.server.core.extension.identity.verification.delegation;

import java.util.Objects;

public class ExternalWorkflowApplicationIdParam {

  String value;

  public ExternalWorkflowApplicationIdParam() {}

  public ExternalWorkflowApplicationIdParam(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ExternalWorkflowApplicationIdParam that = (ExternalWorkflowApplicationIdParam) o;
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
