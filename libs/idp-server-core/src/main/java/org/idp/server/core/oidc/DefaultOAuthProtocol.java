package org.idp.server.core.oidc;

import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
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
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      OAuthSessionDelegate oAuthSessionDelegate) {
    this.requestHandler =
        new OAuthRequestHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository,
            new RequestObjectHttpClient(),
            authorizationGrantedRepository);
    this.authorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            oAuthTokenRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
            clientConfigurationQueryRepository);
    this.oAuthHandler =
        new OAuthHandler(
            authorizationRequestRepository,
            authorizationServerConfigurationQueryRepository,
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

  @Override
  public OAuthPushedRequestResponse push(OAuthPushedRequest oAuthPushedRequest) {
    try {

      OAuthPushedRequestContext context = requestHandler.handlePushedRequest(oAuthPushedRequest);

      return context.createResponse();
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handlePushedRequest(exception);
    }
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

      OAuthRequestContext context =
          requestHandler.handleRequest(oAuthRequest, oAuthSessionDelegate);

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
