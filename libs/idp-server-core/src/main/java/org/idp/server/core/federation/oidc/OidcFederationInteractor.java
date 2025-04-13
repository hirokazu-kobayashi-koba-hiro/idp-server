package org.idp.server.core.federation.oidc;

import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JoseHandler;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.federation.*;
import org.idp.server.core.federation.io.*;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;

public class OidcFederationInteractor implements FederationInteractor {

  FederationConfigurationQueryRepository configurationQueryRepository;
  SsoSessionCommandRepository sessionCommandRepository;
  SsoSessionQueryRepository sessionQueryRepository;
  OidcSsoExecutors oidcSsoExecutors;

  public OidcFederationInteractor(
      OidcSsoExecutors oidcSsoExecutors,
      FederationConfigurationQueryRepository configurationQueryRepository,
      SsoSessionCommandRepository sessionCommandRepository,
      SsoSessionQueryRepository sessionQueryRepository) {
    this.oidcSsoExecutors = oidcSsoExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
  }

  public FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider) {

    OidcSsoConfiguration oidcSsoConfiguration =
        configurationQueryRepository.get(
            tenant, federationType, ssoProvider, OidcSsoConfiguration.class);

    OidcSsoSessionCreator authorizationRequestCreator =
        new OidcSsoSessionCreator(
            oidcSsoConfiguration,
            tenant,
            authorizationRequestIdentifier,
            federationType,
            ssoProvider);
    OidcSsoSession oidcSsoSession = authorizationRequestCreator.create();

    sessionCommandRepository.register(
        tenant, oidcSsoSession.ssoSessionIdentifier(), oidcSsoSession);

    return new FederationRequestResponse(
        FederationRequestStatus.REDIRECABLE_OK, oidcSsoSession, oidcSsoConfiguration);
  }

  public FederationInteractionResult callback(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest federationCallbackRequest,
      UserRepository userRepository) {

    SsoState ssoState = federationCallbackRequest.ssoState();
    FederationCallbackParameters parameters = federationCallbackRequest.parameters();
    OidcSsoSession session =
        sessionQueryRepository.get(tenant, ssoState.ssoSessionIdentifier(), OidcSsoSession.class);

    OidcSsoConfiguration oidcSsoConfiguration =
        configurationQueryRepository.get(
            tenant, federationType, ssoProvider, OidcSsoConfiguration.class);

    OidcSsoExecutor oidcSsoExecutor = oidcSsoExecutors.get(oidcSsoConfiguration.ssoProvider());

    OidcTokenRequestCreator tokenRequestCreator =
        new OidcTokenRequestCreator(parameters, session, oidcSsoConfiguration);
    OidcTokenRequest tokenRequest = tokenRequestCreator.create();
    OidcTokenResponse tokenResponse = oidcSsoExecutor.requestToken(tokenRequest);

    JoseContext joseContext =
        verifyAndParseIdToken(oidcSsoExecutor, oidcSsoConfiguration, tokenResponse);

    OidcUserinfoRequest userinfoRequest =
        new OidcUserinfoRequest(
            oidcSsoConfiguration.userinfoEndpoint(), tokenResponse.accessToken());
    OidcUserinfoResponse userinfoResponse = oidcSsoExecutor.requestUserInfo(userinfoRequest);

    User existingUser =
        userRepository.findByProvider(
            tenant, oidcSsoConfiguration.issuerName(), userinfoResponse.sub());

    OidcUserinfoResponseConvertor convertor =
        new OidcUserinfoResponseConvertor(existingUser, userinfoResponse, oidcSsoConfiguration);
    User user = convertor.convert();

    sessionCommandRepository.delete(tenant, session.ssoSessionIdentifier());

    return FederationInteractionResult.success(session, user);
  }

  private JoseContext verifyAndParseIdToken(
      OidcSsoExecutor oidcSsoExecutor,
      OidcSsoConfiguration configuration,
      OidcTokenResponse tokenResponse) {
    try {
      OidcJwksResponse jwksResponse =
          oidcSsoExecutor.getJwks(new OidcJwksRequest(configuration.jwksUri()));

      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(tokenResponse.idToken(), jwksResponse.value(), "", "");

      joseContext.verifySignature();

      return joseContext;
    } catch (JoseInvalidException e) {

      throw new OidcInvalidIdTokenException("failed to parse id_token", e);
    }
  }
}
