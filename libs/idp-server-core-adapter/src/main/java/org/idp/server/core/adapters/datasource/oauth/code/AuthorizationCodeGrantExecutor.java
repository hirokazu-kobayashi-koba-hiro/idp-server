package org.idp.server.core.adapters.datasource.oauth.code;

import java.util.Map;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;

public interface AuthorizationCodeGrantExecutor {

  void insert(AuthorizationCodeGrant authorizationCodeGrant);

  Map<String, String> selectOne(AuthorizationCode authorizationCode);

  void delete(AuthorizationCodeGrant authorizationCodeGrant);
}
