package org.idp.server.token;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.CustomProperties;

public interface PasswordCredentialsGrantDelegate {

  User findAndAuthenticate(String username, String password);

  CustomProperties getCustomProperties(User user);
}
