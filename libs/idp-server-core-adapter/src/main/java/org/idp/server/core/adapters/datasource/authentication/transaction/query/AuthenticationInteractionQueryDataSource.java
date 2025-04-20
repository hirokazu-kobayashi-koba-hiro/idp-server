package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.authentication.AuthenticationInteractionQueryRepository;
import org.idp.server.core.authentication.exception.MfaTransactionNotFoundException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.Tenant;

public class AuthenticationInteractionQueryDataSource
    implements AuthenticationInteractionQueryRepository {

  AuthenticationTransactionQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationInteractionQueryDataSource() {
    this.executors = new AuthenticationTransactionQuerySqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public <T> T get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, Class<T> clazz) {
    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(identifier, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaTransactionNotFoundException(
          String.format("Mfa transaction is Not Found (%s) (%s)", identifier.value(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
