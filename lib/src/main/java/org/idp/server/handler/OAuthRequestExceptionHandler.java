package org.idp.server.handler;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationErrorResponseBuilder;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oauth.RedirectUri;
import org.idp.server.core.type.oauth.State;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.io.status.OAuthRequestStatus;

public class OAuthRequestExceptionHandler {

  public OAuthRequestResponse handle(OAuthRedirectableBadRequestException exception) {
    OAuthRequestContext context = exception.oAuthRequestContext();
    RedirectUri redirectUri = context.redirectUri();
    TokenIssuer tokenIssuer = context.tokenIssuer();
    ResponseModeValue responseModeValue = new ResponseModeValue("?");
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
