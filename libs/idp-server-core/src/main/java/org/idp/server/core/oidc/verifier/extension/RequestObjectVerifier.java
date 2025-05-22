/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.exception.RequestObjectInvalidException;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;

public class RequestObjectVerifier
    implements AuthorizationRequestExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldNotVerify(OAuthRequestContext context) {
    return !context.isRequestParameterPattern() || context.isUnsignedRequestObject();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      verify(context.joseContext(), context.serverConfiguration(), context.clientConfiguration());
    } catch (RequestObjectInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object", exception.getMessage(), context);
    }
  }
}
