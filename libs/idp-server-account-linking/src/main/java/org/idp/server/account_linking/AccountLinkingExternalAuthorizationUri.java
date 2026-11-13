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

package org.idp.server.account_linking;

import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.http.HttpQueryParams;

/**
 * The authorization request a link sends the browser to at the external IdP.
 *
 * <p>The link's state comes back on the callback and names the session; the nonce is checked
 * against the id_token; the PKCE challenge is of the verifier the session keeps for the code
 * exchange. Without a scope requested at {@code link}, the provider's {@code scopes_supported} is
 * asked for.
 */
public class AccountLinkingExternalAuthorizationUri {

  OidcSsoConfiguration configuration;
  AccountLinkingSession session;

  public AccountLinkingExternalAuthorizationUri(
      OidcSsoConfiguration configuration, AccountLinkingSession session) {
    this.configuration = configuration;
    this.session = session;
  }

  public String value() {
    AccountLinkingPkce pkce = new AccountLinkingPkce(session.codeVerifier());

    HttpQueryParams params = new HttpQueryParams();
    params.add("client_id", configuration.clientId());
    params.add("redirect_uri", configuration.redirectUri());
    params.add("response_type", "code");
    params.add("state", session.state().value());
    params.add("nonce", session.nonce());
    params.add("scope", session.scopeAskedFor(configuration));
    params.add("code_challenge", pkce.codeChallenge());
    params.add("code_challenge_method", pkce.codeChallengeMethod());

    return String.format("%s?%s", configuration.authorizationEndpoint(), params.params());
  }
}
