package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.Map;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface BackchannelAuthenticationRequestSqlExecutor {

  void insert(BackchannelAuthenticationRequest request);

  Map<String, String> selectOne(BackchannelAuthenticationRequestIdentifier identifier);

  void delete(BackchannelAuthenticationRequestIdentifier identifier);
}
