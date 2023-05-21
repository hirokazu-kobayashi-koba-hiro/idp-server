package org.idp.server.oauth.interaction;

import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.CustomProperties;

public class UserInteraction {
  User user;
  Authentication authentication;
  CustomProperties customProperties;
}
