package org.idp.server.core.federation;

import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protcol.FederationApi;
import org.idp.server.core.user.UserService;

public class FederationService implements FederationDelegate {

  FederationApi federationApi;
  UserService userService;

  public FederationService(FederationApi federationApi, UserService userService) {
    this.federationApi = federationApi;
    this.userService = userService;
  }

  public FederationRequestResponse request(FederationRequest federationRequest) {

    return federationApi.handleRequest(federationRequest);
  }

  public FederationCallbackResponse callback(FederationCallbackRequest federationCallbackRequest) {

    return federationApi.handleCallback(federationCallbackRequest, this);
  }

  @Override
  public User find(String tokenIssuer, String providerId, String providerUserId) {

    return userService.findByProvider(tokenIssuer, providerId, providerUserId);
  }
}
