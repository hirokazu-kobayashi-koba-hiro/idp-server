package org.idp.server.core.token;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.type.oauth.Username;

public interface PasswordCredentialsGrantDelegate {

  User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password);
}
