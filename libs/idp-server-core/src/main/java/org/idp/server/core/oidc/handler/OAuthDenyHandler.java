package org.idp.server.core.oidc.handler;

import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.io.OAuthDenyRequest;
import org.idp.server.core.oidc.io.OAuthDenyResponse;
import org.idp.server.core.oidc.io.OAuthDenyStatus;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthDenyHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public OAuthDenyHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public OAuthDenyResponse handle(OAuthDenyRequest request) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);
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
