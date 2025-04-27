package org.idp.server.core.identity.verification.delegation;

public class ExternalWorkflowExaminationStatus {
  String name;
  boolean completed;

  public ExternalWorkflowExaminationStatus() {}

  public ExternalWorkflowExaminationStatus(String name, boolean completed) {
    this.name = name;
    this.completed = completed;
  }

  public String name() {
    return name;
  }

  public boolean completed() {
    return completed;
  }
}
