package org.idp.server.core.adapters.datasource.grantmanagment;

import java.util.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.RequestedClientId;

public class AuthorizationGrantedDataSource implements AuthorizationGrantedRepository {

  JsonConverter jsonConverter;
  AuthorizationGrantedSqlExecutors executors;

  public AuthorizationGrantedDataSource() {
    this.executors = new AuthorizationGrantedSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(authorizationGranted);
  }

  @Override
  public AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant.identifier(), requestedClientId, user);

    if (result == null || result.isEmpty()) {
      return new AuthorizationGranted();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void update(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(authorizationGranted);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
