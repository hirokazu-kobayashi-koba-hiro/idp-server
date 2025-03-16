package org.idp.server.core.handler.oauth;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.handler.oauth.io.OAuthAuthorizeResponse;
import org.idp.server.core.handler.oauth.io.OAuthAuthorizeStatus;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

public class OAuthAuthorizeErrorHandler {

  Logger log = Logger.getLogger(OAuthAuthorizeErrorHandler.class.getName());

  public OAuthAuthorizeResponse handle(Exception exception) {

    if (exception instanceof OAuthBadRequestException badRequestException) {

      log.log(Level.WARNING, badRequestException.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST,
          badRequestException.error().value(),
          badRequestException.errorDescription().value());
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.log(Level.WARNING, redirectableBadRequestException.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new OAuthAuthorizeResponse(
        OAuthAuthorizeStatus.SERVER_ERROR, error.value(), errorDescription.value());
  }
}
