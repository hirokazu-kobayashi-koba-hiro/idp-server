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
import org.idp.server.core.oauth.interaction.OAuthInteractorUnSupportedException;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.tenant.Tenant;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FederationService implements OAuthUserInteractor, FederationDelegate {

  FederationApi federationApi;
  UserManagementApi userManagementApi;

  public FederationService(IdpServerApplication idpServerApplication) {
    this.federationApi = idpServerApplication.federationApi();
    this.userManagementApi = idpServerApplication.userManagementApi();
  }

  //TODO
  @Override
  public OAuthUserInteractionResult interact(Tenant tenant, AuthorizationRequest authorizationRequest, OAuthUserInteractionType type, Map<String, Object> request) {
    switch (type) {
      case FEDERATION_REQUEST -> {

      }
      case FEDERATION_CALLBACK -> {

      }
    }

    throw new OAuthInteractorUnSupportedException(String.format("Federation interaction not supported (%s)", type.name()));
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
