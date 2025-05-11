package org.idp.server.core.oidc;

import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.gateway.RequestObjectHttpClient;
import org.idp.server.core.oidc.handler.*;
import org.idp.server.core.oidc.io.*;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.AuthorizationResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;

/** OAuthApi */
public class DefaultOAuthProtocol implements OAuthProtocol {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authorizeHandler;
  OAuthAuthorizeErrorHandler authAuthorizeErrorHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthDenyErrorHandler denyErrorHandler;
  OAuthHandler oAuthHandler;
  OAuthSessionDelegate oAuthSessionDelegate;

  public DefaultOAuthProtocol(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      OAuthSessionDelegate oAuthSessionDelegate) {
    this.requestHandler =
        new OAuthRequestHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository,
            new RequestObjectHttpClient(),
            authorizationGrantedRepository);
    this.authorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            oAuthTokenRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.oAuthHandler =
        new OAuthHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeErrorHandler = new OAuthAuthorizeErrorHandler();
    this.denyErrorHandler = new OAuthDenyErrorHandler();
    this.oAuthSessionDelegate = oAuthSessionDelegate;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  /**
   * request
   *
   * <p>The authorization endpoint is used to interact with the resource owner and obtain an
   * authorization grant. The authorization server MUST first verify the identity of the resource
   * owner. The way in which the authorization server authenticates the resource owner (e.g.,
   * username and password login, session cookies) is beyond the scope of this specification.
   */
  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {

    try {

      OAuthRequestContext context = requestHandler.handle(oAuthRequest, oAuthSessionDelegate);

      if (context.canAutomaticallyAuthorize()) {

        OAuthAuthorizeRequest oAuthAuthorizeRequest = context.createOAuthAuthorizeRequest();
        AuthorizationResponse response =
            authorizeHandler.handle(oAuthAuthorizeRequest, oAuthSessionDelegate);
        return new OAuthRequestResponse(OAuthRequestStatus.NO_INTERACTION_OK, response);
      }

      return context.createResponse();
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthViewDataResponse getViewData(OAuthViewDataRequest request) {

    return oAuthHandler.handleViewData(request, oAuthSessionDelegate);
  }

  public AuthorizationRequest get(Tenant tenant, AuthorizationRequestIdentifier identifier) {

    return oAuthHandler.handleGettingData(tenant, identifier);
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {

      AuthorizationResponse response = authorizeHandler.handle(request, oAuthSessionDelegate);

      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, response);
    } catch (Exception exception) {

      return authAuthorizeErrorHandler.handle(exception);
    }
  }

  public OAuthDenyResponse deny(OAuthDenyRequest request) {
    try {

      return oAuthDenyHandler.handle(request);
    } catch (Exception exception) {

      return denyErrorHandler.handle(exception);
    }
  }

  public OAuthLogoutResponse logout(OAuthLogoutRequest request) {

    return oAuthHandler.handleLogout(request, oAuthSessionDelegate);
  }
}
