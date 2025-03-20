package org.idp.server.core.token;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.Username;

public interface PasswordCredentialsGrantDelegate {

  User findAndAuthenticate(Tenant tenant, Username username, Password password);
}
