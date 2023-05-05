package org.idp.server.ciba;

import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;

public interface CibaRequestDelegate {
  User find(UserCriteria criteria);

  boolean authenticate(User user, UserCode userCode);

  void notify(User user, BackchannelAuthenticationRequest request);
}
