package org.idp.server.ciba;

import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.TokenIssuer;

public interface CibaRequestDelegate {
  User find(TokenIssuer tokenIssuer, UserCriteria criteria);

  boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode);

  void notify(TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request);

  CustomProperties getCustomProperties(
      TokenIssuer tokenIssuer,
      User user,
      BackchannelAuthenticationRequest backchannelAuthenticationRequest);
}
