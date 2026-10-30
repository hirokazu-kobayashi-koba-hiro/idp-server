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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.core.openid.authentication.Authentication;
import org.junit.jupiter.api.Test;

class LinkingSignInRequirementTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0, 0);

  /** A link started two minutes ago. */
  static final AccountLinkingSession STARTED_TWO_MINUTES_AGO =
      new AccountLinkingSession.Builder()
          .expiresAt(NOW.minusMinutes(2).plusSeconds(AccountLinkingSession.DURATION_SECONDS))
          .build();

  static Authentication signedIn(LocalDateTime time) {
    return new Authentication().setTime(time);
  }

  static boolean recentEnough(Authentication authentication, long maxAuthAgeSeconds) {
    return new LinkingSignInRequirement(maxAuthAgeSeconds, List.of())
        .isMetBy(authentication, STARTED_TWO_MINUTES_AGO, NOW);
  }

  static boolean strongEnough(Authentication authentication, List<String> acrValues) {
    return new LinkingSignInRequirement(300, acrValues)
        .isMetBy(authentication, STARTED_TWO_MINUTES_AGO, NOW);
  }

  @Test
  void acceptsASignInWithinTheLimit() {
    assertTrue(recentEnough(signedIn(NOW.minusMinutes(4)), 300));
  }

  @Test
  void asksAgainForASignInOlderThanTheLimitAndTheLink() {
    assertFalse(recentEnough(signedIn(NOW.minusMinutes(6)), 300));
  }

  /**
   * With 0 the sign-in asked for on the way is the only one accepted; it is after the link began.
   */
  @Test
  void acceptsTheSignInMadeDuringTheLinkEvenWithNoAllowance() {
    assertTrue(recentEnough(signedIn(NOW.minusSeconds(10)), 0));
    assertFalse(recentEnough(signedIn(NOW.minusMinutes(3)), 0));
  }

  @Test
  void asksForASignInWhenThereIsNone() {
    assertFalse(recentEnough(new Authentication(), 300));
    assertFalse(recentEnough(null, 300));
  }

  @Test
  void acceptsAnySignInWhenNoLinkingAcrIsConfigured() {
    assertTrue(strongEnough(signedIn(NOW).addAcr("urn:acr:pwd"), List.of()));
    assertTrue(strongEnough(signedIn(NOW), List.of()));
  }

  /** A recent ordinary sign-in does not stand in for the stronger one the client asks for links. */
  @Test
  void asksAgainForASignInThatDidNotReachTheLinkingAcr() {
    List<String> fidoOnly = List.of("urn:acr:fido");
    assertFalse(strongEnough(signedIn(NOW).addAcr("urn:acr:pwd"), fidoOnly));
    assertFalse(strongEnough(signedIn(NOW), fidoOnly));
    assertFalse(strongEnough(null, fidoOnly));
  }

  @Test
  void acceptsASignInThatReachedOneOfTheLinkingAcrValues() {
    assertTrue(
        strongEnough(signedIn(NOW).addAcr("urn:acr:mfa"), List.of("urn:acr:fido", "urn:acr:mfa")));
  }

  /** Both have to hold: a strong sign-in from long ago does not start a link. */
  @Test
  void asksAgainForAStrongSignInThatIsTooOld() {
    assertFalse(
        new LinkingSignInRequirement(300, List.of("urn:acr:fido"))
            .isMetBy(
                signedIn(NOW.minusMinutes(6)).addAcr("urn:acr:fido"),
                STARTED_TWO_MINUTES_AGO,
                NOW));
  }
}
