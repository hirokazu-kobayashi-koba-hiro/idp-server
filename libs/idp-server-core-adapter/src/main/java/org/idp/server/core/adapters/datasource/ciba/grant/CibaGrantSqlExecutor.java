package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.type.ciba.AuthReqId;

public interface CibaGrantSqlExecutor {

  void insert(CibaGrant cibaGrant);

  void update(CibaGrant cibaGrant);

  Map<String, String> selectOne(AuthReqId authReqId);

  Map<String, String> selectOne(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(CibaGrant cibaGrant);
}
