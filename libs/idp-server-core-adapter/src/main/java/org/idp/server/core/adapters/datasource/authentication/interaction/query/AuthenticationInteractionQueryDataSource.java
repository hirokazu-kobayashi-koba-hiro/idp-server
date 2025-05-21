package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.authentication.exception.MfaTransactionNotFoundException;
import org.idp.server.core.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationInteractionQueryDataSource
    implements AuthenticationInteractionQueryRepository {

  AuthenticationInteractionQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationInteractionQueryDataSource() {
    this.executors = new AuthenticationInteractionQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(Tenant tenant, AuthorizationIdentifier identifier, String type, Class<T> clazz) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaTransactionNotFoundException(
          String.format("Mfa transaction is Not Found (%s) (%s)", identifier.value(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
