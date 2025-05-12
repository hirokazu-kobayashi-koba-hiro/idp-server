package org.idp.server.control_plane.management.identity.verification.io;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdParam;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.exception.IdentityVerificationApplicationConfigurationNotFoundException;

import java.util.*;

public class IdentityVerificationConfigurationRequest implements JsonReadable {
  String id;
  String type;
  String delegation;
  String description;
  String externalWorkflowDelegation;
  String externalWorkflowApplicationIdParam;
  OAuthAuthorizationConfiguration oauthAuthorization;
  Map<String, IdentityVerificationProcessConfiguration> processes;
  List<String> approvedTargetTypes = new ArrayList<>();
  Map<String, Object> verifiedClaimsSchema;

  public IdentityVerificationConfigurationRequest() {}

  public String id() {
    return id;
  }

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

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

  public ExternalWorkflowApplicationIdParam externalWorkflowApplicationIdParam() {
    return new ExternalWorkflowApplicationIdParam(externalWorkflowApplicationIdParam);
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new OAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  public boolean hasAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public Map<String, IdentityVerificationProcessConfiguration> processes() {
    return processes;
  }


  public List<String> approvedTargetTypes() {
    return approvedTargetTypes;
  }

  public String approvedTargetTypesAsString() {
    return String.join(",", approvedTargetTypes);
  }

  public Map<String, Object> verifiedClaimsSchema() {
    return verifiedClaimsSchema;
  }

  public JsonSchemaDefinition verifiedClaimsSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromObject(verifiedClaimsSchema));
  }

  public IdentityVerificationConfiguration toConfiguration(String identifier) {
    return new IdentityVerificationConfiguration(identifier, type, delegation, description, externalWorkflowDelegation, externalWorkflowApplicationIdParam, oauthAuthorization, processes, approvedTargetTypes, verifiedClaimsSchema);
  }
}
