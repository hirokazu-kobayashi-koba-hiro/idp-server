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

import java.util.UUID;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.core.openid.oauth.AccountLinkingReauthentication;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.http.HttpQueryParams;

/**
 * Sends the browser to sign in as the link's user, through the ordinary authorization flow, and
 * back to {@code /linking/start} afterwards.
 *
 * <p>The request is the client's own — its id and a redirect URI it registered — so its
 * authentication policy applies as on any sign-in. {@code login_hint} pins the user who started the
 * link; {@code prompt=login} asks for a sign-in even over a session. The link's state rides along
 * as {@link AccountLinkingReauthentication#STATE_PARAMETER}, which makes the flow end at {@code
 * /linking/start} instead of issuing a code. The PKCE challenge is there for clients that require
 * one; its verifier is thrown away with the code that is never issued.
 *
 * <p>The client's {@code linking_acr_values} go out as {@code acr_values}, so the authentication
 * policy matched on them, rather than the client's ordinary one, applies to this sign-in.
 */
public class AccountLinkingSignInUri {

  String authorizationEndpoint;
  ClientConfiguration client;
  AccountLinkingSession session;

  /**
   * @throws AccountLinkingInvalidRequestException when the client has no redirect URI to sign in
   *     again through
   */
  public AccountLinkingSignInUri(
      String authorizationEndpoint, ClientConfiguration client, AccountLinkingSession session) {
    if (client.redirectUris().isEmpty()) {
      throw new AccountLinkingInvalidRequestException(
          "The client has no redirect URI to sign in again through.");
    }
    this.authorizationEndpoint = authorizationEndpoint;
    this.client = client;
    this.session = session;
  }

  public String value() {
    AccountLinkingPkce pkce = AccountLinkingPkce.generate();
    LinkingSignInRequirement requirement = LinkingSignInRequirement.of(client);

    HttpQueryParams params = new HttpQueryParams();
    params.add("response_type", "code");
    params.add("client_id", session.requestedClientId().value());
    params.add("redirect_uri", client.redirectUris().get(0));
    params.add("scope", "openid");
    params.add("prompt", "login");
    params.add("login_hint", "sub:" + session.userIdentifier().value());
    params.add("state", UUID.randomUUID().toString());
    params.add("nonce", UUID.randomUUID().toString());
    params.add("code_challenge", pkce.codeChallenge());
    params.add("code_challenge_method", pkce.codeChallengeMethod());
    if (requirement.hasAcrValues()) {
      params.add("acr_values", String.join(" ", requirement.acrValues()));
    }
    params.add(AccountLinkingReauthentication.STATE_PARAMETER, session.state().value());

    return String.format("%s?%s", authorizationEndpoint, params.params());
  }
}
