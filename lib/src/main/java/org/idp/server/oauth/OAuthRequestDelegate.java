package org.idp.server.oauth;

import org.idp.server.oauth.interaction.UserInteraction;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public interface OAuthRequestDelegate {

  boolean isValidSession(
      TokenIssuer tokenIssuer, ClientId clientId, AuthorizationRequest authorizationRequest);

  UserInteraction getUserInteraction(
      TokenIssuer tokenIssuer, ClientId clientId, AuthorizationRequest authorizationRequest);

  void registerSession(TokenIssuer tokenIssuer, ClientId clientId, OAuthSession oAuthSession);
}
