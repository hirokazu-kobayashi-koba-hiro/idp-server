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

package org.idp.server.core.openid.oauth.verifier.extension;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.date.SystemDateTime;

/** PushedAuthorizationRequestVerifier RFC 9126 Section 2.2 - request_uri expiration check */
public class PushedAuthorizationRequestVerifier implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return context.isPushedRequest();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    if (context.authorizationRequest().isExpired(SystemDateTime.now())) {
      throw new OAuthBadRequestException(
          "invalid_request_uri", "pushed authorization request_uri has expired", context.tenant());
    }
  }
}
