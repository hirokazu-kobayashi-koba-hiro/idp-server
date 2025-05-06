package org.idp.server.core.oidc.handler;

import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;
import org.idp.server.core.oidc.io.OAuthDenyRequest;
import org.idp.server.core.oidc.io.OAuthDenyResponse;
import org.idp.server.core.oidc.io.OAuthDenyStatus;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.*;

public class OAuthDenyHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthDenyHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthDenyResponse handle(OAuthDenyRequest request) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);
    AuthorizationDenyErrorResponseCreator authorizationDenyErrorResponseCreator =
        new AuthorizationDenyErrorResponseCreator(
            authorizationRequest,
            request.denyReason(),
            authorizationServerConfiguration,
            clientConfiguration);

    AuthorizationErrorResponse errorResponse = authorizationDenyErrorResponseCreator.create();

    return new OAuthDenyResponse(OAuthDenyStatus.OK, errorResponse);
  }
}
