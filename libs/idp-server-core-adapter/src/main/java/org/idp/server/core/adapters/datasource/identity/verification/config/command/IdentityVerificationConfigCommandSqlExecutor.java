package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationConfigCommandSqlExecutor {
  void insert(
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
