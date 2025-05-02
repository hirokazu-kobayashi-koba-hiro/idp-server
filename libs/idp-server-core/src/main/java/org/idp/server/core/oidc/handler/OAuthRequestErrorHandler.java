package org.idp.server.core.oidc.handler;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.core.oidc.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.io.OAuthRequestStatus;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;
import org.idp.server.core.oidc.response.AuthorizationErrorResponseCreator;

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
