package org.idp.server.core.identity.verification.configuration;

import java.util.List;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type);

  IdentityVerificationConfiguration find(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier);

  List<IdentityVerificationConfiguration> findList(Tenant tenant, int limit, int offset);
}
