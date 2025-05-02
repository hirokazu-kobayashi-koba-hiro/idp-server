package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface CibaGrantSqlExecutor {

  void insert(CibaGrant cibaGrant);

  void update(CibaGrant cibaGrant);

  Map<String, String> selectOne(AuthReqId authReqId);

  Map<String, String> selectOne(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(CibaGrant cibaGrant);
}
