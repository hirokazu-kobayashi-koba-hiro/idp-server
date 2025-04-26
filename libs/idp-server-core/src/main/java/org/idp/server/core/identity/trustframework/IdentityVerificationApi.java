package org.idp.server.core.identity.trustframework;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationRequest;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.security.RequestAttributes;

public interface IdentityVerificationApi {
  IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes);

  IdentityVerificationApplicationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes);

  void callbackProcess();

  void callbackResult();
}
