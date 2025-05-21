package org.idp.server.core.oidc.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.core.oidc.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.io.OAuthPushedRequestResponse;
import org.idp.server.core.oidc.io.OAuthPushedRequestStatus;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.io.OAuthRequestStatus;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;
import org.idp.server.core.oidc.response.AuthorizationErrorResponseCreator;
import org.idp.server.core.oidc.view.OAuthViewUrlResolver;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthRequestErrorHandler.class);

  public OAuthPushedRequestResponse handlePushedRequest(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", badRequestException.error().value());
      response.put("error_description", badRequestException.errorDescription().value());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      log.warn(redirectableBadRequestException.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", redirectableBadRequestException.error().value());
      response.put("error_description", redirectableBadRequestException.errorDescription().value());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn("not found client configuration");
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", exception.getMessage());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn("not found authorization server configuration");
      log.warn(exception.getMessage());
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", exception.getMessage());
      return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.BAD_REQUEST, response);
    }

    log.error(exception.getMessage(), exception);
    Map<String, Object> response = new HashMap<>();
    response.put("error", "server_error");
    response.put("error_description", exception.getMessage());
    return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.SERVER_ERROR, response);
  }

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
