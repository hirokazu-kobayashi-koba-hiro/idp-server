package org.idp.server.handler.oauth;

import org.idp.server.handler.oauth.io.OAuthRequestResponse;
import org.idp.server.handler.oauth.io.OAuthRequestStatus;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationErrorResponseBuilder;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.RedirectUri;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthRequestExceptionHandler {

  public OAuthRequestResponse handle(OAuthRedirectableBadRequestException exception) {
    OAuthRequestContext context = exception.oAuthRequestContext();
    RedirectUri redirectUri = context.redirectUri();
    TokenIssuer tokenIssuer = context.tokenIssuer();
    ResponseModeValue responseModeValue = context.responseModeValue();
    State state = context.state();
    AuthorizationErrorResponseBuilder builder =
        new AuthorizationErrorResponseBuilder(redirectUri, responseModeValue, tokenIssuer)
            .add(state)
            .add(exception.error())
            .add(exception.errorDescription());
    AuthorizationErrorResponse errorResponse = builder.build();
    return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST, errorResponse);
  }
}
