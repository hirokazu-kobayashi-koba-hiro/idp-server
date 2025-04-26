package org.idp.server.core.identity.trustframework;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationRequest;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.security.RequestAttributes;

public interface IdentityVerificationApi {
  IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      User user,
      IdentityVerificationType identityVerificationType,
      VerificationProcess verificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes);

  void callbackProcess();

  void callbackResult();
}
