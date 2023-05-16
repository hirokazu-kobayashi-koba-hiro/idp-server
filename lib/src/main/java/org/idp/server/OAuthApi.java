package org.idp.server;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthDenyHandler;
import org.idp.server.handler.oauth.OAuthRequestErrorHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.SessionIdentifier;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  OAuthDenyHandler oAuthDenyHandler;
  OAuthRequestDelegate oAuthRequestDelegate;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      OAuthRequestHandler requestHandler,
      OAuthAuthorizeHandler authAuthorizeHandler,
      OAuthDenyHandler oAuthDenyHandler) {
    this.requestHandler = requestHandler;
    this.oAuthRequestErrorHandler = new OAuthRequestErrorHandler();
    this.authAuthorizeHandler = authAuthorizeHandler;
    this.oAuthDenyHandler = oAuthDenyHandler;
  }

  /**
   * request
   *
   * <p>The authorization endpoint is used to interact with the resource owner and obtain an
   * authorization grant. The authorization server MUST first verify the identity of the resource
   * owner. The way in which the authorization server authenticates the resource owner (e.g.,
   * username and password login, session cookies) is beyond the scope of this specification.
   *
   * @param oAuthRequest
   * @return
   */
  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {
    try {
      OAuthRequestContext context = requestHandler.handle(oAuthRequest);
      if (requestHandler.isAuthorizable(oAuthRequest, context, oAuthRequestDelegate)) {
        SessionIdentifier sessionIdentifier = oAuthRequest.toSessionIdentifier();
        TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
        AuthorizationRequest authorizationRequest = context.authorizationRequest();
        User user =
            oAuthRequestDelegate.getUser(sessionIdentifier, tokenIssuer, authorizationRequest);
        CustomProperties customProperties =
            oAuthRequestDelegate.getCustomProperties(
                sessionIdentifier, tokenIssuer, user, authorizationRequest);
        OAuthAuthorizeRequest oAuthAuthorizeRequest =
            new OAuthAuthorizeRequest(
                    context.authorizationRequestIdentifier().value(), tokenIssuer.value(), user)
                .setCustomProperties(customProperties.values());
        AuthorizationResponse response = authAuthorizeHandler.handle(oAuthAuthorizeRequest);
        return new OAuthRequestResponse(OAuthRequestStatus.NO_INTERACTION_OK, response);
      }
      return new OAuthRequestResponse(OAuthRequestStatus.OK, context);
    } catch (Exception exception) {
      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {
      AuthorizationResponse response = authAuthorizeHandler.handle(request);
      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, response);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthAuthorizeResponse();
    }
  }

  public OAuthDenyResponse deny(OAuthDenyRequest request) {
    try {
      return oAuthDenyHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthDenyResponse();
    }
  }

  public void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate) {
    this.oAuthRequestDelegate = oAuthRequestDelegate;
  }
}
