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
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.type.oidc.ResponseMode;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;

public class JarmVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldVerify(OAuthRequestContext oAuthRequestContext) {
    return oAuthRequestContext.responseMode().isJwtMode();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    ResponseMode responseMode = oAuthRequestContext.responseMode();

    // TODO support
    if (responseMode.isFormPostJwt()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client", "response_mode form_post_jwt is unsupported", oAuthRequestContext);
    }
  }
}
