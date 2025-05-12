package org.idp.server.core.identity.verification.configuration;

import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigurationCommandRepository {

  void register(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration);

  void update(
          Tenant tenant,
          IdentityVerificationType type,
          IdentityVerificationConfiguration configuration);

  void delete(Tenant tenant, IdentityVerificationType type, IdentityVerificationConfiguration configuration);
}
