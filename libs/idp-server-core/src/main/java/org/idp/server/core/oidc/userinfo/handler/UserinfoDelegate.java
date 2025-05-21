package org.idp.server.core.oidc.userinfo.handler;

import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserinfoDelegate {
  User findUser(Tenant tenant, Subject subject);
}
