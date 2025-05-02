package org.idp.server.core.oidc.userinfo.handler;

import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface UserinfoDelegate {
  User findUser(Tenant tenant, Subject subject);
}
