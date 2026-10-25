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

package org.idp.server.account_linking.verifier;

import java.time.LocalDateTime;
import org.idp.server.account_linking.AccountLinkingScope;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.token.OAuthToken;

/**
 * Decides whether an authenticated client may have a user's stored external token.
 *
 * <p>The token is released only when every check holds: the user's token is live, names a user, is
 * a plain bearer token issued to that same client and carries {@code linked_account:read_token},
 * the user is active, and the client is allowed the account's provider.
 */
public class LinkedExternalAccountTokenVerifier {

  /** The user's access token, as the client presented it. */
  public void verifyUserToken(OAuthToken userToken, ClientConfiguration client, LocalDateTime now) {
    verifyTokenExistsAndNotExpired(userToken, now);
    verifyNotClientCredentialsToken(userToken);
    verifyNotSenderConstrained(userToken);
    verifyIssuedToAuthenticatedClient(userToken, client);
    verifyHasReadTokenScope(userToken);
  }

  private void verifyTokenExistsAndNotExpired(OAuthToken userToken, LocalDateTime now) {
    if (!userToken.exists() || userToken.isExpiredAccessToken(now)) {
      throw LinkedExternalAccountTokenException.invalidToken("token is invalid or expired.");
    }
  }

  /** A client credentials token acts for the client itself; there is no user to read tokens of. */
  private void verifyNotClientCredentialsToken(OAuthToken userToken) {
    if (userToken.isClientCredentialsGrant() || !userToken.hasSubject()) {
      throw LinkedExternalAccountTokenException.invalidToken("token does not name a user.");
    }
  }

  /**
   * Refuses sender-constrained tokens.
   *
   * <p>A sender-constrained token is only worth something if the proof is checked. Until this
   * endpoint verifies DPoP proofs and certificate thumbprints, it refuses such tokens instead of
   * accepting them as if they were bearer tokens.
   */
  private void verifyNotSenderConstrained(OAuthToken userToken) {
    if (userToken.accessToken().hasDPoPBinding()
        || userToken.accessToken().hasClientCertification()
        || userToken.accessToken().hasClientInstanceBinding()) {
      throw LinkedExternalAccountTokenException.invalidRequest(
          "Sender-constrained user tokens are not supported by this endpoint yet.");
    }
  }

  /**
   * The client presenting the user's token must be the one it was issued to.
   *
   * <p>Otherwise any confidential client holding a leaked user token could read that user's
   * external tokens.
   */
  private void verifyIssuedToAuthenticatedClient(OAuthToken userToken, ClientConfiguration client) {
    if (!client.isIdentifiedBy(userToken.requestedClientId().value())) {
      throw LinkedExternalAccountTokenException.unauthorizedClient(
          "token was not issued to the authenticated client.");
    }
  }

  /** The user granted this client {@code linked_account:read_token}. */
  private void verifyHasReadTokenScope(OAuthToken userToken) {
    if (!AccountLinkingScope.READ_TOKEN.isGrantedBy(userToken)) {
      throw LinkedExternalAccountTokenException.insufficientScope(
          "token does not carry " + AccountLinkingScope.READ_TOKEN.value() + ".");
    }
  }

  /** The user the token names. */
  public void verifyUserIsActive(User user) {
    if (!user.isActive()) {
      throw LinkedExternalAccountTokenException.invalidToken("The user is not active.");
    }
  }

  /** The client's {@code linking_token_providers} against the account's provider. */
  public void verifyProviderInLinkingTokenProviders(
      ClientConfiguration client, LinkedExternalAccount account) {
    if (!client.linkingTokenProviders().contains(account.provider().value())) {
      throw LinkedExternalAccountTokenException.unauthorizedClient(
          "The client is not allowed to retrieve tokens of this provider.");
    }
  }
}
