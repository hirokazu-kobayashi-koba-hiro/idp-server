package org.idp.server.core.identity.verification.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.exception.IdentityVerificationApplicationConfigurationNotFoundException;

public class IdentityVerificationConfiguration implements JsonReadable {
  String type;
  String delegation;
  String description;
  String externalWorkflowDelegation;
  IdentityVerificationOAuthAuthorizationConfiguration oauthAuthorization;
  Map<String, IdentityVerificationProcessConfiguration> processes;
  List<String> approvedTargetTypes = new ArrayList<>();

  public IdentityVerificationConfiguration() {}

  public IdentityVerificationType type() {
    return new IdentityVerificationType(type);
  }

  public String delegation() {
    return delegation;
  }

  public String description() {
    return description;
  }

  public ExternalWorkflowDelegation externalWorkflowDelegation() {
    return new ExternalWorkflowDelegation(externalWorkflowDelegation);
  }

  public IdentityVerificationOAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new IdentityVerificationOAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  public boolean hasAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public Map<String, IdentityVerificationProcessConfiguration> processes() {
    return processes;
  }

  public IdentityVerificationProcessConfiguration getProcessConfig(
      IdentityVerificationProcess process) {
    if (!processes.containsKey(process.name())) {
      throw new IdentityVerificationApplicationConfigurationNotFoundException(
          "invalid configuration. type: " + process.name() + "is unregistered.");
    }
    return processes.get(process.name());
  }

  public List<String> approvedTargetTypes() {
    return approvedTargetTypes;
  }

  public String approvedTargetTypesAsString() {
    return String.join(",", approvedTargetTypes);
  }
}
