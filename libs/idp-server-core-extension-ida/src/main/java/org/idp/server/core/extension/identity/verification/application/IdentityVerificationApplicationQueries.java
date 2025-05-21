package org.idp.server.core.extension.identity.verification.application;

import java.util.Map;

public class IdentityVerificationApplicationQueries {

  Map<String, String> values;

  public IdentityVerificationApplicationQueries() {}

  public IdentityVerificationApplicationQueries(Map<String, String> values) {
    this.values = values;
  }

  public boolean hasId() {
    return values.containsKey("id");
  }

  public String id() {
    return values.get("id");
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public String type() {
    return values.get("type");
  }

  public boolean hasClientId() {
    return values.containsKey("client_id");
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasExternalWorkflowApplicationId() {
    return values.containsKey("external_workflow_application_id");
  }

  public String externalWorkflowApplicationId() {
    return values.get("external_workflow_application_id");
  }

  public boolean hasExternalWorkflowDelegation() {
    return values.containsKey("external_workflow_delegation");
  }

  public String externalWorkflowDelegation() {
    return values.get("external_workflow_delegation");
  }

  public boolean hasTrustFramework() {
    return values.containsKey("trust_framework");
  }

  public String trustFramework() {
    return values.get("trust_framework");
  }

  public boolean hasStatus() {
    return values.containsKey("status");
  }

  public String status() {
    return values.get("status");
  }
}
