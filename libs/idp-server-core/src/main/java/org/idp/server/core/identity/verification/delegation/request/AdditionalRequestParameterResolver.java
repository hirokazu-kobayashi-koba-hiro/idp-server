package org.idp.server.core.identity.verification.delegation.request;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AdditionalRequestParameterResolver {

  default boolean shouldResolve(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration) {
    return false;
  }

  Map<String, Object> resolve(Tenant tenant, User user, IdentityVerificationApplications applications, IdentityVerificationType type, IdentityVerificationProcess processes, IdentityVerificationRequest request, IdentityVerificationConfiguration verificationConfiguration);
}
