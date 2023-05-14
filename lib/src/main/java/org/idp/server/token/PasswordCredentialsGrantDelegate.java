package org.idp.server.token;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.Password;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oauth.Username;

public interface PasswordCredentialsGrantDelegate {

  User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password);

  CustomProperties getCustomProperties(TokenIssuer tokenIssuer, User user);
}
