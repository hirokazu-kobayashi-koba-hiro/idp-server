package org.idp.server.core.handler.oauth;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.oauth.io.OAuthDenyRequest;
import org.idp.server.core.handler.oauth.io.OAuthDenyResponse;
import org.idp.server.core.handler.oauth.io.OAuthDenyStatus;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.*;

public class OAuthDenyHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public OAuthDenyHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthDenyResponse handle(OAuthDenyRequest request) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.clientId();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);
    AuthorizationDenyErrorResponseCreator authorizationDenyErrorResponseCreator =
        new AuthorizationDenyErrorResponseCreator(
            authorizationRequest, request.denyReason(), serverConfiguration, clientConfiguration);

    AuthorizationErrorResponse errorResponse = authorizationDenyErrorResponseCreator.create();

    return new OAuthDenyResponse(OAuthDenyStatus.OK, errorResponse);
  }
}
