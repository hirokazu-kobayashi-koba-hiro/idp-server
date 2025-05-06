package org.idp.server.core.oidc.handler;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.io.OAuthAuthorizeResponse;
import org.idp.server.core.oidc.io.OAuthAuthorizeStatus;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;
import org.idp.server.core.oidc.response.AuthorizationErrorResponseCreator;

public class OAuthAuthorizeErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthAuthorizeErrorHandler.class);

  public OAuthAuthorizeResponse handle(Exception exception) {

    if (exception instanceof OAuthBadRequestException badRequestException) {

      log.warn(badRequestException.getMessage());
      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.BAD_REQUEST, badRequestException.error().value(), badRequestException.errorDescription().value());
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator = new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.warn(redirectableBadRequestException.getMessage());
      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.BAD_REQUEST, error.value(), errorDescription.value());
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.error(exception.getMessage(), exception);
    return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.SERVER_ERROR, error.value(), errorDescription.value());
  }
}
