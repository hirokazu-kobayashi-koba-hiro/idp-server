package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.io.OAuthAuthorizeRequest;
import org.idp.server.handler.io.OAuthAuthorizeResponse;
import org.idp.server.handler.io.OAuthRequest;
import org.idp.server.handler.io.OAuthRequestResponse;
import org.idp.server.handler.io.status.OAuthAuthorizeStatus;
import org.idp.server.handler.io.status.OAuthRequestStatus;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthRequestExceptionHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.response.AuthorizationResponse;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestHandler requestHandler;
  OAuthRequestExceptionHandler oAuthRequestExceptionHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(OAuthRequestHandler requestHandler, OAuthAuthorizeHandler authAuthorizeHandler) {
    this.requestHandler = requestHandler;
    this.oAuthRequestExceptionHandler = new OAuthRequestExceptionHandler();
    this.authAuthorizeHandler = authAuthorizeHandler;
  }

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
      AuthorizationResponse authorizationResponse = authAuthorizeHandler.handle(request);

      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, authorizationResponse);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthAuthorizeResponse();
    }
  }
}
