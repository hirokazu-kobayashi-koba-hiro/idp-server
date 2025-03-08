package org.idp.server.application.service.federation;

import org.idp.server.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.FederationApi;
import org.idp.server.core.federation.FederationDelegate;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.springframework.stereotype.Service;

@Service
public class FederationService implements FederationDelegate {

  FederationApi federationApi;
  UserService userService;

  public FederationService(IdpServerApplication idpServerApplication, UserService userService) {
    this.federationApi = idpServerApplication.federationApi();
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

    return userService.findBy(tokenIssuer, providerId, providerUserId);
  }
}
