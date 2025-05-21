package org.idp.server.core.extension.identity.verification.configuration;

import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigurationCommandRepository {

  void register(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration);

  void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration);

  void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration);
}
