package org.idp.server.handler.oauth;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.oauth.io.OAuthRequestResponse;
import org.idp.server.handler.oauth.io.OAuthRequestStatus;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationErrorResponseBuilder;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.RedirectUri;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthRequestErrorHandler {

  Logger log = Logger.getLogger(OAuthRequestErrorHandler.class.getName());

  public OAuthRequestResponse handle(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST,
          badRequestException.error(),
          badRequestException.errorDescription());
    }
    if (exception instanceof OAuthRedirectableBadRequestException redirectableBadRequestException) {
      OAuthRequestContext context = redirectableBadRequestException.oAuthRequestContext();
      RedirectUri redirectUri = context.redirectUri();
      TokenIssuer tokenIssuer = context.tokenIssuer();
      ResponseModeValue responseModeValue = context.responseModeValue();
      State state = context.state();
      AuthorizationErrorResponseBuilder builder =
          new AuthorizationErrorResponseBuilder(redirectUri, responseModeValue, tokenIssuer)
              .add(state)
              .add(redirectableBadRequestException.error())
              .add(redirectableBadRequestException.errorDescription());
      AuthorizationErrorResponse errorResponse = builder.build();
      return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST);
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST);
    }
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR);
  }
}