package org.idp.server.core.handler.userinfo;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.Subject;

public interface UserinfoDelegate {
  User findUser(Tenant tenant, Subject subject);
}
