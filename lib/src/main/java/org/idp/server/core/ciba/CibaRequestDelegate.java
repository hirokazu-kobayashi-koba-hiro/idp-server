package org.idp.server.core.ciba;

import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.ciba.UserCode;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface CibaRequestDelegate {
  User find(TokenIssuer tokenIssuer, UserCriteria criteria);

  boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode);

  void notify(TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request);
}
