/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.basic.type.extension.ResponseModeValue;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.ResponseMode;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;

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
