package org.idp.server.handler.userinfo;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;

public interface UserinfoDelegate {
  User getUser(Subject subject);
}
