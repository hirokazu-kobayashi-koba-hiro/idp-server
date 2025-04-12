package org.idp.server.core.oauth.handler;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.*;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.oauth.io.OAuthRequest;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.OAuthRequestParameters;
import org.idp.server.core.oauth.service.*;
import org.idp.server.core.oauth.validator.OAuthRequestValidator;
import org.idp.server.core.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.core.tenant.Tenant;

/** OAuthRequestHandler */
public class OAuthRequestHandler {

  OAuthRequestContextServices oAuthRequestContextServices;
  OAuthRequestVerifier verifier;
  AuthorizationRequestRepository authorizationRequestRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  AuthorizationGrantedRepository grantedRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway,
      AuthorizationGrantedRepository grantedRepository) {
    this.oAuthRequestContextServices = new OAuthRequestContextServices(requestObjectGateway);
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.grantedRepository = grantedRepository;
  }

  public OAuthRequestContext handle(OAuthRequest oAuthRequest, OAuthSessionDelegate delegate) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    Tenant tenant = oAuthRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(parameters);
    validator.validate();

    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, parameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = parameters.analyzePattern();
    OAuthRequestContextService oAuthRequestContextService =
        oAuthRequestContextServices.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextService.create(
            tenant, parameters, serverConfiguration, clientConfiguration);
    verifier.verify(context);

    authorizationRequestRepository.register(context.authorizationRequest());

    OAuthSession session = delegate.find(context.sessionKey());

    if (session.exists()) {
      context.setSession(session);

      AuthorizationGranted authorizationGranted =
          grantedRepository.find(tenant.identifier(), parameters.clientId(), session.user());
      context.setAuthorizationGranted(authorizationGranted);
    }

    return context;
  }
}
