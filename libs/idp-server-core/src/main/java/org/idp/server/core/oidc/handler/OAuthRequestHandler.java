package org.idp.server.core.oidc.handler;

import org.idp.server.core.grant_management.AuthorizationGranted;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;
import org.idp.server.core.oidc.context.*;
import org.idp.server.core.oidc.gateway.RequestObjectGateway;
import org.idp.server.core.oidc.io.OAuthRequest;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.validator.OAuthRequestValidator;
import org.idp.server.core.oidc.verifier.OAuthRequestVerifier;

/** OAuthRequestHandler */
public class OAuthRequestHandler {

  OAuthRequestContextCreators oAuthRequestContextCreators;
  OAuthRequestVerifier verifier;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  AuthorizationGrantedRepository grantedRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway,
      AuthorizationGrantedRepository grantedRepository) {
    this.oAuthRequestContextCreators = new OAuthRequestContextCreators(requestObjectGateway);
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.grantedRepository = grantedRepository;
  }

  public OAuthRequestContext handle(OAuthRequest oAuthRequest, OAuthSessionDelegate delegate) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    Tenant tenant = oAuthRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(parameters);
    validator.validate();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, parameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = parameters.analyzePattern();
    OAuthRequestContextCreator oAuthRequestContextCreator =
        oAuthRequestContextCreators.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextCreator.create(
            tenant, parameters, authorizationServerConfiguration, clientConfiguration);
    verifier.verify(context);

    authorizationRequestRepository.register(tenant, context.authorizationRequest());

    OAuthSession session = delegate.find(context.sessionKey());

    if (session.exists()) {
      context.setSession(session);

      AuthorizationGranted authorizationGranted =
          grantedRepository.find(tenant, parameters.clientId(), session.user());
      context.setAuthorizationGranted(authorizationGranted);
    }

    return context;
  }
}
