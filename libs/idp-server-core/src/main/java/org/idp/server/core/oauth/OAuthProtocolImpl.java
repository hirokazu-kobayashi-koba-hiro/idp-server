package org.idp.server.core.oauth;

import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.oauth.handler.*;
import org.idp.server.core.oauth.io.*;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;

/** OAuthApi */
public class OAuthProtocolImpl implements OAuthProtocol {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authorizeHandler;
  OAuthAuthorizeErrorHandler authAuthorizeErrorHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthDenyErrorHandler denyErrorHandler;
  OAuthHandler oAuthHandler;
  OAuthRequestDelegate oAuthRequestDelegate;

  public OAuthProtocolImpl(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      RequestObjectGateway requestObjectGateway,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      OAuthRequestDelegate oAuthRequestDelegate) {
    this.requestHandler =
        new OAuthRequestHandler(
            authorizationRequestRepository,
            serverConfigurationRepository,
            clientConfigurationRepository,
            requestObjectGateway,
            authorizationGrantedRepository);
    this.authorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            oAuthTokenRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.oAuthHandler =
        new OAuthHandler(
            authorizationRequestRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeErrorHandler = new OAuthAuthorizeErrorHandler();
    this.denyErrorHandler = new OAuthDenyErrorHandler();
    this.oAuthRequestDelegate = oAuthRequestDelegate;
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

      OAuthRequestContext context = requestHandler.handle(oAuthRequest, oAuthRequestDelegate);

      if (context.canAutomaticallyAuthorize()) {

        OAuthAuthorizeRequest oAuthAuthorizeRequest = context.createOAuthAuthorizeRequest();
        AuthorizationResponse response =
            authorizeHandler.handle(oAuthAuthorizeRequest, oAuthRequestDelegate);
        return new OAuthRequestResponse(OAuthRequestStatus.NO_INTERACTION_OK, response);
      }

      return context.createResponse();
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthViewDataResponse getViewData(OAuthViewDataRequest request) {

    return oAuthHandler.handleViewData(request, oAuthRequestDelegate);
  }

  public AuthorizationRequest get(AuthorizationRequestIdentifier identifier) {

    return oAuthHandler.handleGettingData(identifier);
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {

      AuthorizationResponse response = authorizeHandler.handle(request, oAuthRequestDelegate);

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

    return oAuthHandler.handleLogout(request, oAuthRequestDelegate);
  }
}
