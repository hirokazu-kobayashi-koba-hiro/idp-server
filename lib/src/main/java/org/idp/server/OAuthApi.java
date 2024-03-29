package org.idp.server;

import org.idp.server.handler.oauth.*;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  OAuthAuthorizeErrorHandler authAuthorizeErrorHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthDenyErrorHandler denyErrorHandler;
  OAuthRequestDelegate oAuthRequestDelegate;

  OAuthApi(
      OAuthRequestHandler requestHandler,
      OAuthAuthorizeHandler authAuthorizeHandler,
      OAuthDenyHandler oAuthDenyHandler) {
    this.requestHandler = requestHandler;
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeHandler = authAuthorizeHandler;
    this.authAuthorizeErrorHandler = new OAuthAuthorizeErrorHandler();
    this.oAuthDenyHandler = oAuthDenyHandler;
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
      if (requestHandler.isAuthorizable(context, session, oAuthRequestDelegate)) {
        TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
        OAuthAuthorizeRequest oAuthAuthorizeRequest =
            new OAuthAuthorizeRequest(
                    context.authorizationRequestIdentifier().value(),
                    tokenIssuer.value(),
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

  public void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate) {
    this.oAuthRequestDelegate = oAuthRequestDelegate;
  }
}
