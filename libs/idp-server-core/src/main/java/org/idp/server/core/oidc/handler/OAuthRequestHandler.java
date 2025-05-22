package org.idp.server.core.oidc.handler;

import org.idp.server.core.oidc.*;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.context.*;
import org.idp.server.core.oidc.gateway.RequestObjectGateway;
import org.idp.server.core.oidc.grant_management.AuthorizationGranted;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.io.OAuthPushedRequest;
import org.idp.server.core.oidc.io.OAuthRequest;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.validator.OAuthRequestValidator;
import org.idp.server.core.oidc.verifier.OAuthRequestVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthRequestHandler */
public class OAuthRequestHandler {

  OAuthRequestContextCreators oAuthRequestContextCreators;
  OAuthRequestVerifier verifier;
  ClientAuthenticationHandler clientAuthenticationHandler;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationGrantedRepository grantedRepository;

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      RequestObjectGateway requestObjectGateway,
      AuthorizationGrantedRepository grantedRepository) {
    this.oAuthRequestContextCreators =
        new OAuthRequestContextCreators(requestObjectGateway, authorizationRequestRepository);
    this.verifier = new OAuthRequestVerifier();
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.grantedRepository = grantedRepository;
  }

  public OAuthPushedRequestContext handlePushedRequest(OAuthPushedRequest pushedRequest) {
    OAuthRequestParameters requestParameters = pushedRequest.toOAuthRequestParameters();
    Tenant tenant = pushedRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(tenant, requestParameters);
    validator.validate();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestParameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = requestParameters.analyzePattern();
    OAuthRequestContextCreator oAuthRequestContextCreator =
        oAuthRequestContextCreators.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextCreator.create(
            tenant, requestParameters, authorizationServerConfiguration, clientConfiguration);
    verifier.verify(context);

    OAuthPushedRequestContext oAuthPushedRequestContext =
        new OAuthPushedRequestContext(
            context,
            pushedRequest.clientSecretBasic(),
            pushedRequest.toClientCert(),
            pushedRequest.toBackchannelParameters());
    clientAuthenticationHandler.authenticate(oAuthPushedRequestContext);

    authorizationRequestRepository.register(tenant, context.authorizationRequest());

    return oAuthPushedRequestContext;
  }

  public OAuthRequestContext handleRequest(
      OAuthRequest oAuthRequest, OAuthSessionDelegate delegate) {
    OAuthRequestParameters parameters = oAuthRequest.toParameters();
    Tenant tenant = oAuthRequest.tenant();
    OAuthRequestValidator validator = new OAuthRequestValidator(tenant, parameters);
    validator.validate();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, parameters.clientId());

    OAuthRequestPattern oAuthRequestPattern = parameters.analyzePattern();
    OAuthRequestContextCreator oAuthRequestContextCreator =
        oAuthRequestContextCreators.get(oAuthRequestPattern);

    OAuthRequestContext context =
        oAuthRequestContextCreator.create(
            tenant, parameters, authorizationServerConfiguration, clientConfiguration);
    verifier.verify(context);

    if (!context.isPushedRequest()) {
      authorizationRequestRepository.register(tenant, context.authorizationRequest());
    }

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
