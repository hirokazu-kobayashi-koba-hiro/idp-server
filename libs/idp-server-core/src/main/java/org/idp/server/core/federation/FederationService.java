package org.idp.server.core.federation;

import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protocol.FederationProtocol;
import org.idp.server.core.user.UserService;

public class FederationService implements FederationDelegate {

  FederationProtocol federationProtocol;
  UserService userService;

  public FederationService(FederationProtocol federationProtocol, UserService userService) {
    this.federationProtocol = federationProtocol;
    this.userService = userService;
  }

  public FederationRequestResponse request(FederationRequest federationRequest) {

    return federationProtocol.handleRequest(federationRequest);
  }

  public FederationCallbackResponse callback(FederationCallbackRequest federationCallbackRequest) {

    return federationProtocol.handleCallback(federationCallbackRequest, this);
  }

  @Override
  public User find(String tokenIssuer, String providerId, String providerUserId) {

    return userService.findByProvider(tokenIssuer, providerId, providerUserId);
  }
}
