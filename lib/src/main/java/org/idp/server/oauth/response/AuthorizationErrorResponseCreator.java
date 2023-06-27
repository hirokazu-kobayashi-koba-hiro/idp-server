package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.type.extension.JarmPayload;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.ResponseMode;

public class AuthorizationErrorResponseCreator
    implements RedirectUriDecidable, ResponseModeDecidable, JarmCreatable {

  OAuthRedirectableBadRequestException exception;

  public AuthorizationErrorResponseCreator(OAuthRedirectableBadRequestException exception) {
    this.exception = exception;
  }

  public AuthorizationErrorResponse create() {
    OAuthRequestContext context = exception.oAuthRequestContext();
    RedirectUri redirectUri = context.redirectUri();
    TokenIssuer tokenIssuer = context.tokenIssuer();
    ResponseModeValue responseModeValue = context.responseModeValue();
    ResponseMode responseMode = context.responseMode();
    State state = context.state();
    AuthorizationErrorResponseBuilder builder =
        new AuthorizationErrorResponseBuilder(
                redirectUri, responseMode, responseModeValue, tokenIssuer)
            .add(state)
            .add(exception.error())
            .add(exception.errorDescription());
    if (context.isJwtMode()) {
      AuthorizationErrorResponse errorResponse = builder.build();
      JarmPayload jarmPayload =
          createResponse(
              errorResponse, context.serverConfiguration(), context.clientConfiguration());
      builder.add(jarmPayload);
    }

    return builder.build();
  }
}
