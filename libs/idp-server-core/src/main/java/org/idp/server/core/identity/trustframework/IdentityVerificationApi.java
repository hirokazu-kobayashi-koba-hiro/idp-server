package org.idp.server.core.identity.trustframework;

import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;

public interface IdentityVerificationApi {
  IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType identityVerificationType,
      VerificationProcess verificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes);

  void callbackProcess();

  void callbackResult();
}
