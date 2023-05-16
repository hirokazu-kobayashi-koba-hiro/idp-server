package org.idp.server.oauth;

import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.SessionIdentifier;
import org.idp.server.type.oauth.TokenIssuer;

public interface OAuthRequestDelegate {

  boolean isValidSession(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      AuthorizationRequest authorizationRequest);

  User getUser(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      AuthorizationRequest authorizationRequest);

  CustomProperties getCustomProperties(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      User user,
      AuthorizationRequest authorizationRequest);

  void registerSession(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      User user,
      AuthorizationRequest authorizationRequest);
}
