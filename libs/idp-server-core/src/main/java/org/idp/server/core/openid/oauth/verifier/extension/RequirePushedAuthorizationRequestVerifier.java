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
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;

/**
 * Enforces the server-wide {@code require_pushed_authorization_requests} metadata (RFC 9126 Section
 * 5) across every authorization profile.
 *
 * <p>When the authorization server advertises {@code require_pushed_authorization_requests: true},
 * per RFC 9126 it "accepts authorization request data only via PAR". This verifier rejects any
 * direct authorization request (one that did not originate from a {@code request_uri} issued by the
 * PAR endpoint), so the advertised metadata is truthful for OAuth 2.0 / OIDC tenants as well — not
 * only for FAPI 2.0, which mandates PAR through {@code FapiSecurity20Verifier} regardless of this
 * flag.
 *
 * <p>The check is skipped at the PAR endpoint itself ({@link
 * OAuthRequestContext#isAtPushedEndpoint()} {@code == true}), where the request is by definition
 * being pushed. It runs as an extension verifier, i.e. after the base verifier has validated {@code
 * redirect_uri} / client, so a redirectable error can be returned safely.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9126.html#section-5">RFC 9126 Section 5</a>
 */
public class RequirePushedAuthorizationRequestVerifier
    implements AuthorizationRequestExtensionVerifier {

  @Override
  public boolean shouldVerify(OAuthRequestContext oAuthRequestContext) {
    return oAuthRequestContext.serverConfiguration().requirePushedAuthorizationRequests()
        && !oAuthRequestContext.isAtPushedEndpoint();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {
    if (!oAuthRequestContext.isPushedRequest()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "This authorization server requires Pushed Authorization Requests (PAR, RFC 9126)."
              + " Direct authorization requests are not allowed.",
          oAuthRequestContext);
    }
  }
}
