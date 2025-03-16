package org.idp.server.core.handler.userinfo;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface UserinfoDelegate {
  User findUser(TokenIssuer tokenIssuer, Subject subject);
}
