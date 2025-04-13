package org.idp.server.core.adapters.datasource.oauth.code;

import java.util.Map;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.type.oauth.AuthorizationCode;

public interface AuthorizationCodeGrantExecutor {

  void insert(AuthorizationCodeGrant authorizationCodeGrant);

  Map<String, String> selectOne(AuthorizationCode authorizationCode);

  void delete(AuthorizationCodeGrant authorizationCodeGrant);
}
