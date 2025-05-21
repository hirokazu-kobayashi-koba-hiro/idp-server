package org.idp.server.core.oidc.token;

import org.idp.server.basic.type.oauth.Password;
import org.idp.server.basic.type.oauth.Username;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface PasswordCredentialsGrantDelegate {

  User findAndAuthenticate(Tenant tenant, Username username, Password password);
}
