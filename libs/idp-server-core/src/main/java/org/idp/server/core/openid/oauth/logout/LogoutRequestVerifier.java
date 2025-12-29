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

package org.idp.server.core.openid.oauth.logout;

import java.util.List;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * LogoutRequestVerifier
 *
 * <p>Validates RP-Initiated Logout requests according to the OpenID Connect RP-Initiated Logout 1.0
 * specification.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class LogoutRequestVerifier {

  /**
   * Verifies the logout request.
   *
   * @param context the logout context
   * @throws OAuthBadRequestException if verification fails
   */
  public void verify(OAuthLogoutContext context) {
    verifyPostLogoutRedirectUri(context);
    verifyIdTokenHintAudience(context);
  }

  /**
   * OpenID Connect RP-Initiated Logout 1.0 Section 4:
   *
   * <p>"post_logout_redirect_uri ... This URI MUST have been previously registered with the OP,
   * either using the post_logout_redirect_uris Registration parameter or via another mechanism."
   *
   * <p>"If the post_logout_redirect_uri value is provided but is not valid for the identified
   * Client (or no Client was identified), the OP MUST NOT perform post-logout redirection..."
   *
   * <p>Note: Client identification is always guaranteed since id_token_hint is required and each
   * IdTokenHintContextCreator validates client existence.
   *
   * @param context the logout context
   * @throws OAuthBadRequestException if validation fails
   */
  private void verifyPostLogoutRedirectUri(OAuthLogoutContext context) {
    if (!context.hasPostLogoutRedirectUri()) {
      return;
    }

    String postLogoutRedirectUri = context.postLogoutRedirectUri().value();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    if (!clientConfiguration.hasPostLogoutRedirectUris()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "post_logout_redirect_uri provided but no redirect URIs registered for this client",
          context.tenant());
    }

    if (!clientConfiguration.isRegisteredPostLogoutRedirectUri(postLogoutRedirectUri)) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "post_logout_redirect_uri does not match any registered post_logout_redirect_uris (%s)",
              postLogoutRedirectUri),
          context.tenant());
    }
  }

  /**
   * OpenID Connect RP-Initiated Logout 1.0:
   *
   * <p>If client_id parameter is provided, it must match the aud claim in id_token_hint. This
   * prevents using an id_token issued for a different client.
   *
   * @param context the logout context
   * @throws OAuthBadRequestException if validation fails
   */
  private void verifyIdTokenHintAudience(OAuthLogoutContext context) {
    if (!context.parameters().hasClientId()) {
      return;
    }

    JsonWebTokenClaims claims = context.idTokenClaims();
    List<String> audienceList = claims.getAud();
    String clientIdParameter = context.parameters().clientId().value();

    if (audienceList == null || !audienceList.contains(clientIdParameter)) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "id_token_hint aud claim does not contain client_id parameter (expected: %s, got: %s)",
              clientIdParameter, audienceList),
          context.tenant());
    }
  }
}
