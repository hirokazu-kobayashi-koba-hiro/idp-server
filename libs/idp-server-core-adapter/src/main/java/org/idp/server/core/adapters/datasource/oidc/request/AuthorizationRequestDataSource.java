package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.exception.OAuthRequestNotFoundException;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  AuthorizationRequestSqlExecutors executors;

  public AuthorizationRequestDataSource() {
    this.executors = new AuthorizationRequestSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, AuthorizationRequest authorizationRequest) {

    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authorizationRequest);
  }

  @Override
  public AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new OAuthRequestNotFoundException(
          "invalid_request",
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, authorizationRequestIdentifier);
  }
}
