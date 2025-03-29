package org.idp.server.core.handler.oauth;

import java.util.Objects;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.oauth.io.OAuthRequest;
import org.idp.server.core.handler.oauth.io.OAuthRequestResponse;
import org.idp.server.core.handler.oauth.io.OAuthRequestStatus;
import org.idp.server.core.oauth.*;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
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

  public OAuthRequestHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway) {
    this.oAuthRequestContextServices = new OAuthRequestContextServices(requestObjectGateway);
    this.verifier = new OAuthRequestVerifier();
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthRequestContext handle(OAuthRequest oAuthRequest) {
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
        oAuthRequestContextService.create(parameters, serverConfiguration, clientConfiguration);
    verifier.verify(context);
    authorizationRequestRepository.register(context.authorizationRequest());

    return context;
  }

  public boolean canAuthorize(
      OAuthRequestContext context,
      OAuthSession session,
      OAuthRequestDelegate oAuthRequestDelegate) {
    if (!context.isPromptNone()) {
      return false;
    }
    if (Objects.isNull(oAuthRequestDelegate)) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session registration function is disable", context);
    }
    if (Objects.isNull(session) || !session.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is not registered", context);
    }
    if (!session.isValid(context.authorizationRequest())) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is invalid", context);
    }
    return true;
  }

  public OAuthRequestResponse handleResponse(OAuthRequestContext context, OAuthSession session) {
    if (context.isPromptCreate()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK_ACCOUNT_CREATION, context, session);
    }
    if (Objects.isNull(session) || !session.exists()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, context, session);
    }
    if (!session.isValid(context.authorizationRequest())) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, context, session);
    }
    return new OAuthRequestResponse(OAuthRequestStatus.OK_SESSION_ENABLE, context, session);
  }
}
