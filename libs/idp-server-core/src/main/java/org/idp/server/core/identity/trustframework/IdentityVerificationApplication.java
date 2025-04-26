package org.idp.server.core.identity.trustframework;

import org.idp.server.core.identity.trustframework.delegation.WorkflowApplyingResult;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  IdentityVerificationApplicationDetails details;
  String sub;
  TrustFramework trustFramework;

  public static IdentityVerificationApplication create(
      IdentityVerificationApplicationRequest request, WorkflowApplyingResult applyingResult) {
    return null;
  }
}
