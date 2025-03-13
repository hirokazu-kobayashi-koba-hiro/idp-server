package org.idp.server.adapters.springboot.application.service.federation;

import org.idp.server.core.UserManagementApi;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.FederationApi;
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
  UserManagementApi userManagementApi;

  public FederationService(IdpServerApplication idpServerApplication) {
    this.federationApi = idpServerApplication.federationApi();
    this.userManagementApi = idpServerApplication.userManagementApi();
  }

  public FederationRequestResponse request(FederationRequest federationRequest) {

    return federationApi.handleRequest(federationRequest);
  }

  public FederationCallbackResponse callback(FederationCallbackRequest federationCallbackRequest) {

    return federationApi.handleCallback(federationCallbackRequest, this);
  }

  @Override
  public User find(String tokenIssuer, String providerId, String providerUserId) {

    return userManagementApi.findByProvider(tokenIssuer, providerId, providerUserId);
  }
}
