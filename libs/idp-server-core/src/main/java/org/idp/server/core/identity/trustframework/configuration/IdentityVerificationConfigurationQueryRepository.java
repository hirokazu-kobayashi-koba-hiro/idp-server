package org.idp.server.core.identity.trustframework.configuration;

import org.idp.server.core.identity.trustframework.IdentityVerificationType;
import org.idp.server.core.tenant.Tenant;

public interface IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type);
}
