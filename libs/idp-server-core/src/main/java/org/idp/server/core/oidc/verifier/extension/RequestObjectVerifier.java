/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.exception.RequestObjectInvalidException;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;

public class RequestObjectVerifier
    implements AuthorizationRequestExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return context.isRequestParameterPattern() && !context.isUnsignedRequestObject();
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
