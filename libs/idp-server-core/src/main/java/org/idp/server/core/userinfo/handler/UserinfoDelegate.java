package org.idp.server.core.userinfo.handler;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.Subject;

public interface UserinfoDelegate {
  User findUser(Tenant tenant, Subject subject);
}
