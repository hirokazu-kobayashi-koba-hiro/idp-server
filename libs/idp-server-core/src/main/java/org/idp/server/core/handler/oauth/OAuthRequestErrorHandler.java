package org.idp.server.core.handler.oauth;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.handler.oauth.io.OAuthRequestResponse;
import org.idp.server.core.handler.oauth.io.OAuthRequestStatus;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oauth.Error;

public class OAuthRequestErrorHandler {

  Logger log = Logger.getLogger(OAuthRequestErrorHandler.class.getName());

  public OAuthRequestResponse handle(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.log(Level.WARNING, exception.getMessage());
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST,
          badRequestException.error(),
          badRequestException.errorDescription());
    }
    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.log(Level.WARNING, redirectableBadRequestException.getMessage());
      return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST, error, errorDescription);
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST, error, errorDescription);
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR, error, errorDescription);
  }
}
