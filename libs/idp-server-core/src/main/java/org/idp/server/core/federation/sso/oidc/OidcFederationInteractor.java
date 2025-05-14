package org.idp.server.core.federation.sso.oidc;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.federation.*;
import org.idp.server.core.federation.io.*;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.sso.SsoProvider;
import org.idp.server.core.federation.sso.SsoSessionCommandRepository;
import org.idp.server.core.federation.sso.SsoSessionQueryRepository;
import org.idp.server.core.federation.sso.SsoState;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

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
      UserQueryRepository userQueryRepository) {

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
        userQueryRepository.findByProvider(
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
