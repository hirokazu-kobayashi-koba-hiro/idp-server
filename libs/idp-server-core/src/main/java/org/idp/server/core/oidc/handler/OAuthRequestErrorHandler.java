package org.idp.server.core.oidc.handler;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.io.OAuthRequestStatus;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;
import org.idp.server.core.oidc.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.oidc.view.OAuthViewUrlResolver;

public class OAuthRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthRequestErrorHandler.class);

  public OAuthRequestResponse handle(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              badRequestException.tenant(),
              badRequestException.error(),
              badRequestException.errorDescription());
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST,
          frontUrl,
          badRequestException.error(),
          badRequestException.errorDescription());
    }
    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      AuthorizationErrorResponseCreator authorizationErrorResponseCreator =
          new AuthorizationErrorResponseCreator(redirectableBadRequestException);
      AuthorizationErrorResponse errorResponse = authorizationErrorResponseCreator.create();
      log.warn(redirectableBadRequestException.getMessage());
      return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception
        instanceof ClientConfigurationNotFoundException clientConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              clientConfigurationNotFoundException.tenant(), error, errorDescription);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, frontUrl, error, errorDescription);
    }
    if (exception
        instanceof ServerConfigurationNotFoundException serverConfigurationNotFoundException) {
      log.warn("not found configuration");
      log.warn(exception.getMessage());
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      String frontUrl =
          OAuthViewUrlResolver.resolveError(
              serverConfigurationNotFoundException.tenant(), error, errorDescription);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, frontUrl, error, errorDescription);
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.error(exception.getMessage(), exception);
    return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR, "", error, errorDescription);
  }
}
