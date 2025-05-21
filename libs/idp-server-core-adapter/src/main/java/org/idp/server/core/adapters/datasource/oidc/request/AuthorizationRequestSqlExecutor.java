package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthorizationRequestSqlExecutor {

  void insert(Tenant tenant, AuthorizationRequest authorizationRequest);

  Map<String, String> selectOne(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);

  void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
