package org.idp.server.core.adapters.datasource.identity.verification.config;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.identity.verification.exception.IdentityVerificationConfigurationNotFoundException;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationConfigurationQueryDataSource
    implements IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigurationQueryDataSource() {
    this.executors = new IdentityVerificationConfigSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type) {
    IdentityVerificationConfigSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, type.name());

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new IdentityVerificationConfigurationNotFoundException(
          String.format(
              "IdentityVerification Configuration is Not Found (%s) (%s)",
              tenant.identifierValue(), type.name()));
    }

    return jsonConverter.read(result.get("payload"), IdentityVerificationConfiguration.class);
  }
}
