package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthDenyHandler;
import org.idp.server.handler.oauth.OAuthRequestExceptionHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestHandler requestHandler;
  OAuthRequestExceptionHandler oAuthRequestExceptionHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  OAuthDenyHandler oAuthDenyHandler;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      OAuthRequestHandler requestHandler,
      OAuthAuthorizeHandler authAuthorizeHandler,
      OAuthDenyHandler oAuthDenyHandler) {
    this.requestHandler = requestHandler;
    this.oAuthRequestExceptionHandler = new OAuthRequestExceptionHandler();
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
      OAuthRequestContext oAuthRequestContext = requestHandler.handle(oAuthRequest);

      return new OAuthRequestResponse(OAuthRequestStatus.OK, oAuthRequestContext);
    } catch (OAuthBadRequestException exception) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, exception.error(), exception.errorDescription());
    } catch (OAuthRedirectableBadRequestException exception) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return oAuthRequestExceptionHandler.handle(exception);
    } catch (ServerConfigurationNotFoundException
        | ClientConfigurationNotFoundException exception) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR);
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
