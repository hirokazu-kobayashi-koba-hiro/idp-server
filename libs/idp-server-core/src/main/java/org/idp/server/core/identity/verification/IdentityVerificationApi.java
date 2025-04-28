package org.idp.server.core.identity.verification;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.security.RequestAttributes;

public interface IdentityVerificationApi {
  IdentityVerificationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes);

  IdentityVerificationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes);

  IdentityVerificationResponse callbackExamination(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request);

  IdentityVerificationResponse callbackResult(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request);
}
