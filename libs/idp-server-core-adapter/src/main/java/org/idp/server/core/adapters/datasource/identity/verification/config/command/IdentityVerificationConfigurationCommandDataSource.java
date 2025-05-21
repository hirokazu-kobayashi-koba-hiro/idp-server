package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigurationCommandDataSource
    implements IdentityVerificationConfigurationCommandRepository {

  IdentityVerificationConfigCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigurationCommandDataSource() {
    this.executors = new IdentityVerificationConfigCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, type, configuration);
  }

  @Override
  public void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, type, configuration);
  }

  @Override
  public void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    IdentityVerificationConfigCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, type, configuration);
  }
}
