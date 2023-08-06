package org.idp.server.handler.oauth;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.oauth.io.OAuthDenyResponse;
import org.idp.server.handler.oauth.io.OAuthDenyStatus;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationErrorResponseCreator;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class OAuthDenyErrorHandler {

  Logger log = Logger.getLogger(OAuthDenyErrorHandler.class.getName());

  public OAuthDenyResponse handle(Exception exception) {
    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.log(Level.WARNING, redirectableBadRequestException.getMessage(), exception);
      return new OAuthDenyResponse(OAuthDenyStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthDenyResponse(
          OAuthDenyStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthDenyResponse(
          OAuthDenyStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new OAuthDenyResponse(
        OAuthDenyStatus.SERVER_ERROR, error.value(), errorDescription.value());
  }
}
