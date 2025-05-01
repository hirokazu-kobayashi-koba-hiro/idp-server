package org.idp.server.core.identity.verification.configuration;

import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type);
}
