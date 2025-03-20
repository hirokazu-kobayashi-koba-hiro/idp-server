package org.idp.server.core.protocol;

import org.idp.server.core.handler.oauth.*;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.tenant.Tenant;

/** OAuthApi */
public class OAuthProtocolImpl implements OAuthProtocol {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  OAuthAuthorizeErrorHandler authAuthorizeErrorHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthDenyErrorHandler denyErrorHandler;
  OAuthHandler oauthHandler;
  OAuthRequestDelegate oAuthRequestDelegate;

  public OAuthProtocolImpl(
      OAuthRequestHandler requestHandler,
      OAuthAuthorizeHandler authAuthorizeHandler,
      OAuthDenyHandler oAuthDenyHandler,
      OAuthHandler oauthHandler) {
    this.requestHandler = requestHandler;
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeHandler = authAuthorizeHandler;
    this.authAuthorizeErrorHandler = new OAuthAuthorizeErrorHandler();
    this.oAuthDenyHandler = oAuthDenyHandler;
    this.oauthHandler = oauthHandler;
    this.denyErrorHandler = new OAuthDenyErrorHandler();
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

      OAuthRequestContext context = requestHandler.handle(oAuthRequest);
      OAuthSession session = oAuthRequestDelegate.findSession(context.sessionKey());

      if (requestHandler.canAuthorize(context, session, oAuthRequestDelegate)) {
        Tenant tenant = oAuthRequest.tenant();
        OAuthAuthorizeRequest oAuthAuthorizeRequest =
            new OAuthAuthorizeRequest(
                    tenant,
                    context.authorizationRequestIdentifier().value(),
                    session.user(),
                    session.authentication())
                .setCustomProperties(session.customProperties());
        AuthorizationResponse response =
            authAuthorizeHandler.handle(oAuthAuthorizeRequest, oAuthRequestDelegate);
        return new OAuthRequestResponse(OAuthRequestStatus.NO_INTERACTION_OK, response);
      }

      return requestHandler.handleResponse(context, session);
    } catch (Exception exception) {

      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthViewDataResponse getViewData(OAuthViewDataRequest request) {

    return oauthHandler.handleViewData(request, oAuthRequestDelegate);
  }

  public AuthorizationRequest get(AuthorizationRequestIdentifier identifier) {

    return oauthHandler.handleGettingData(identifier);
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {

      AuthorizationResponse response = authAuthorizeHandler.handle(request, oAuthRequestDelegate);

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

    return oauthHandler.handleLogout(request, oAuthRequestDelegate);
  }

  public void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate) {
    this.oAuthRequestDelegate = oAuthRequestDelegate;
  }
}
