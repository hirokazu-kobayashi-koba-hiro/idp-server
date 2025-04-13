package org.idp.server.core.adapters.datasource.oauth.request;

import java.util.Map;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

public interface AuthorizationRequestSqlExecutor {

  void insert(AuthorizationRequest authorizationRequest);

  Map<String, String> selectOne(AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
