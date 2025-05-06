package org.idp.server.core.identity.verification;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface IdentityVerificationApi {
  IdentityVerificationResponse apply(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, IdentityVerificationType identityVerificationType, IdentityVerificationProcess identityVerificationProcess, IdentityVerificationRequest request, RequestAttributes requestAttributes);

  IdentityVerificationResponse findApplications(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, IdentityVerificationApplicationQueries queries, RequestAttributes requestAttributes);

  IdentityVerificationResponse process(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, IdentityVerificationApplicationIdentifier identifier, IdentityVerificationType identityVerificationType, IdentityVerificationProcess identityVerificationProcess, IdentityVerificationRequest request, RequestAttributes requestAttributes);

  IdentityVerificationResponse callbackExaminationForStaticPath(TenantIdentifier tenantIdentifier, IdentityVerificationType type, IdentityVerificationRequest request, RequestAttributes requestAttributes);

  IdentityVerificationResponse callbackResultForStaticPath(TenantIdentifier tenantIdentifier, IdentityVerificationType identityVerificationType, IdentityVerificationRequest request, RequestAttributes requestAttributes);

  IdentityVerificationResponse delete(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, IdentityVerificationApplicationIdentifier identifier, IdentityVerificationType type, RequestAttributes requestAttributes);
}
