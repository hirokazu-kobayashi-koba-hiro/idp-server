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
package org.idp.server.account_linking.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.core.openid.authentication.Authentication;
import org.junit.jupiter.api.Test;

class AccountLinkingSignInFreshnessTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0, 0);

  /** A link started two minutes ago: its session expires 900 seconds after it started. */
  static final AccountLinkingSession STARTED_TWO_MINUTES_AGO =
      new AccountLinkingSession.Builder()
          .expiresAt(
              NOW.minusMinutes(2).plusSeconds(AccountLinkingStartHandler.SESSION_DURATION_SECONDS))
          .build();

  static Authentication signedIn(LocalDateTime time) {
    return new Authentication().setTime(time);
  }

  @Test
  void acceptsASignInWithinTheLimit() {
    assertTrue(
        AccountLinkingAuthorizeHandler.isRecentEnough(
            signedIn(NOW.minusMinutes(4)), STARTED_TWO_MINUTES_AGO, 300, NOW));
  }

  @Test
  void asksAgainForASignInOlderThanTheLimitAndTheLink() {
    assertFalse(
        AccountLinkingAuthorizeHandler.isRecentEnough(
            signedIn(NOW.minusMinutes(6)), STARTED_TWO_MINUTES_AGO, 300, NOW));
  }

  /**
   * With 0 the sign-in asked for on the way is the only one accepted; it is after the link began.
   */
  @Test
  void acceptsTheSignInMadeDuringTheLinkEvenWithNoAllowance() {
    assertTrue(
        AccountLinkingAuthorizeHandler.isRecentEnough(
            signedIn(NOW.minusSeconds(10)), STARTED_TWO_MINUTES_AGO, 0, NOW));
    assertFalse(
        AccountLinkingAuthorizeHandler.isRecentEnough(
            signedIn(NOW.minusMinutes(3)), STARTED_TWO_MINUTES_AGO, 0, NOW));
  }

  @Test
  void asksForASignInWhenThereIsNone() {
    assertFalse(
        AccountLinkingAuthorizeHandler.isRecentEnough(
            new Authentication(), STARTED_TWO_MINUTES_AGO, 300, NOW));
    assertFalse(
        AccountLinkingAuthorizeHandler.isRecentEnough(null, STARTED_TWO_MINUTES_AGO, 300, NOW));
  }

  @Test
  void acceptsAnySignInWhenNoLinkingAcrIsConfigured() {
    assertTrue(
        AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(
            signedIn(NOW).addAcr("urn:acr:pwd"), List.of()));
    assertTrue(AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(signedIn(NOW), List.of()));
  }

  /** A recent ordinary sign-in does not stand in for the stronger one the client asks for links. */
  @Test
  void asksAgainForASignInThatDidNotReachTheLinkingAcr() {
    List<String> fidoOnly = List.of("urn:acr:fido");
    assertFalse(
        AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(
            signedIn(NOW).addAcr("urn:acr:pwd"), fidoOnly));
    assertFalse(AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(signedIn(NOW), fidoOnly));
    assertFalse(AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(null, fidoOnly));
  }

  @Test
  void acceptsASignInThatReachedOneOfTheLinkingAcrValues() {
    assertTrue(
        AccountLinkingAuthorizeHandler.hasReachedLinkingAcr(
            signedIn(NOW).addAcr("urn:acr:mfa"), List.of("urn:acr:fido", "urn:acr:mfa")));
  }
}
