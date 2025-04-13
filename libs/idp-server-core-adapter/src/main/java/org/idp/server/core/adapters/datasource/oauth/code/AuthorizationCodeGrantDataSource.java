package org.idp.server.core.adapters.datasource.oauth.code;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  AuthorizationCodeGrantExecutors executors;
  JsonConverter jsonConverter;

  public AuthorizationCodeGrantDataSource() {
    this.executors = new AuthorizationCodeGrantExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {

    AuthorizationCodeGrantExecutor executor = executors.get(tenant.dialect());
    executor.insert(authorizationCodeGrant);
  }

  @Override
  public AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.dialect());
    Map<String, String> stringMap = executor.selectOne(authorizationCode);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationCodeGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.dialect());
    executor.delete(authorizationCodeGrant);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
