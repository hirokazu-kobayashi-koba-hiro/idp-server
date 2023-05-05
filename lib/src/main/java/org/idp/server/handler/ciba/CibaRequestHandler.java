package org.idp.server.handler.ciba;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.ciba.*;
import org.idp.server.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.grant.CibaGrantFactory;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.ciba.response.BackchannelAuthenticationResponseBuilder;
import org.idp.server.ciba.service.CibaRequestContextService;
import org.idp.server.ciba.service.NormalPatternContextService;
import org.idp.server.ciba.service.RequestObjectPatternContextService;
import org.idp.server.ciba.validator.CibaRequestValidator;
import org.idp.server.ciba.verifier.CibaRequestVerifier;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.ciba.io.CibaRequest;
import org.idp.server.handler.ciba.io.CibaRequestResponse;
import org.idp.server.handler.ciba.io.CibaRequestStatus;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.TokenIssuer;

public class CibaRequestHandler {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  CibaRequestAnalyzer analyzer;
  Map<CibaRequestPattern, CibaRequestContextService> contextServices;
  CibaRequestValidator validator;
  CibaRequestVerifier verifier;
  CibaGrantFactory cibaGrantFactory;
  ClientAuthenticatorHandler clientAuthenticatorHandler;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CibaRequestHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.cibaGrantRepository = cibaGrantRepository;
    this.analyzer = new CibaRequestAnalyzer();
    this.contextServices = new HashMap<>();
    this.contextServices.put(CibaRequestPattern.NORMAL, new NormalPatternContextService());
    this.contextServices.put(
        CibaRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
    this.validator = new CibaRequestValidator();
    this.verifier = new CibaRequestVerifier();
    this.cibaGrantFactory = new CibaGrantFactory();
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaRequestResponse handle(CibaRequest request, CibaRequestDelegate delegate) {
    CibaRequestParameters parameters = request.toParameters();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    validator.validate(parameters);
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, request.clientId());
    CibaRequestPattern pattern = analyzer.analyze(parameters);
    CibaRequestContextService cibaRequestContextService = contextServices.get(pattern);
    if (Objects.isNull(cibaRequestContextService)) {
      throw new RuntimeException("unsupported ciba request pattern");
    }
    CibaRequestContext context =
        cibaRequestContextService.create(
            request.clientSecretBasic(), parameters, serverConfiguration, clientConfiguration);

    verifier.verify(context);
    clientAuthenticatorHandler.authenticate(context);

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        context.backchannelAuthenticationRequest();
    User user =
        delegate.find(
            new UserCriteria(
                backchannelAuthenticationRequest.loginHint(),
                backchannelAuthenticationRequest.loginHintToken(),
                backchannelAuthenticationRequest.idTokenHint()));
    if (!user.exists()) {
      throw new BackchannelAuthenticationBadRequestException(
          "unknown_user_id",
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
    }
    if (context.hasUserCode()) {
      boolean authenticationResult = delegate.authenticate(user, context.userCode());
      if (!authenticationResult) {
        throw new BackchannelAuthenticationBadRequestException(
            "invalid_user_code", "backchannel authentication request user_code is invalid");
      }
    }
    delegate.notify(user, backchannelAuthenticationRequest);
    BackchannelAuthenticationResponse response =
        new BackchannelAuthenticationResponseBuilder()
            .add(new AuthReqId(UUID.randomUUID().toString()))
            .add(context.expiresIn())
            .add(context.interval())
            .build();

    backchannelAuthenticationRequestRepository.register(backchannelAuthenticationRequest);
    CibaGrant cibaGrant = cibaGrantFactory.create(context, response, user);
    cibaGrantRepository.register(cibaGrant);

    return new CibaRequestResponse(CibaRequestStatus.OK, response);
  }
}
