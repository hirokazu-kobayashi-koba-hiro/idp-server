package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public interface AuthorizationRequestSqlExecutor {

  void insert(AuthorizationRequest authorizationRequest);

  Map<String, String> selectOne(AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
