package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthDenyHandler;
import org.idp.server.handler.oauth.OAuthRequestErrorHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.handler.oauth.io.*;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestHandler requestHandler;
  OAuthRequestErrorHandler oAuthRequestErrorHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  OAuthDenyHandler oAuthDenyHandler;
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
      return requestHandler.handle(oAuthRequest);
    } catch (Exception exception) {
      return oAuthRequestErrorHandler.handle(exception);
    }
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    try {
      return authAuthorizeHandler.handle(request);
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
}
