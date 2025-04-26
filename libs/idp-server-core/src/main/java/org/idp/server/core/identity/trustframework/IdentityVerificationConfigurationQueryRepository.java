package org.idp.server.core.identity.trustframework;

import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type);
}
