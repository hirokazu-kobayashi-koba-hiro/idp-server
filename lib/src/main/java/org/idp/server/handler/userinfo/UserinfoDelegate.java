package org.idp.server.handler.userinfo;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;

public interface UserinfoDelegate {
  User findUser(TokenIssuer tokenIssuer, Subject subject);
}
