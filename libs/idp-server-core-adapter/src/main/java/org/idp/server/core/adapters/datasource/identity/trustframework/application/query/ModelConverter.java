package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationDetails;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationStatus;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationDetails;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowDelegation;
import org.idp.server.core.identity.verification.trustframework.TrustFramework;
import org.idp.server.core.identity.verification.trustframework.TrustFrameworkDetails;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class ModelConverter {

  static IdentityVerificationApplication convert(Map<String, String> map) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(map.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(map.get("tenant_id"));
    RequestedClientId requestedClientId = new RequestedClientId(map.get("client_id"));
    IdentityVerificationType verificationType =
        new IdentityVerificationType(map.get("verification_type"));
    String sub = map.get("user_id");
    IdentityVerificationApplicationDetails details =
        new IdentityVerificationApplicationDetails(
            JsonNodeWrapper.fromString(map.get("application_details")));

    ExternalWorkflowDelegation externalWorkflowDelegation =
        new ExternalWorkflowDelegation(map.get("external_workflow_delegation"));
    ExternalWorkflowApplicationIdentifier externalApplicationId =
        new ExternalWorkflowApplicationIdentifier(map.get("external_application_id"));
    ExternalWorkflowApplicationDetails externalWorkflowApplicationDetails =
        new ExternalWorkflowApplicationDetails(
            JsonNodeWrapper.fromString(map.get("external_application_details")));

    TrustFramework trustFramework = new TrustFramework(map.get("trust_framework"));
    TrustFrameworkDetails trustFrameworkDetails =
        new TrustFrameworkDetails(JsonNodeWrapper.fromString(map.get("trust_framework_details")));

    LocalDateTime requestedAt = LocalDateTime.parse(map.get("requested_at"));
    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        details,
        sub,
        externalWorkflowDelegation,
        externalApplicationId,
        externalWorkflowApplicationDetails,
        trustFramework,
        trustFrameworkDetails,
        IdentityVerificationApplicationStatus.REQUESTED,
        requestedAt);
  }
}
