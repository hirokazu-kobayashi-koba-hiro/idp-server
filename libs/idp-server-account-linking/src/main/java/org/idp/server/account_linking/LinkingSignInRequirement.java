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

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;

/**
 * What the browser's sign-in must meet to start a link: the client's {@code linking_max_auth_age}
 * and {@code linking_acr_values}.
 */
public class LinkingSignInRequirement {

  long maxAuthAgeSeconds;
  List<String> acrValues;

  public LinkingSignInRequirement(long maxAuthAgeSeconds, List<String> acrValues) {
    this.maxAuthAgeSeconds = maxAuthAgeSeconds;
    this.acrValues = acrValues;
  }

  public static LinkingSignInRequirement of(ClientConfiguration client) {
    return new LinkingSignInRequirement(client.linkingMaxAuthAge(), client.linkingAcrValues());
  }

  /** Whether {@code authentication} is recent enough and strong enough to start {@code session}. */
  public boolean isMetBy(
      Authentication authentication, AccountLinkingSession session, LocalDateTime now) {
    return isRecentEnough(authentication, session, now) && hasReachedAcr(authentication);
  }

  /**
   * Recent enough is within {@code linking_max_auth_age}, or after the link was started — that is,
   * the sign-in this server asked for on the way. Without the second, a limit of 0 could never be
   * met, since the sign-in is always a moment before the browser returns.
   */
  private boolean isRecentEnough(
      Authentication authentication, AccountLinkingSession session, LocalDateTime now) {
    if (authentication == null || authentication.time() == null) {
      return false;
    }
    LocalDateTime signedIn = authentication.time();
    return !signedIn.isBefore(now.minusSeconds(maxAuthAgeSeconds))
        || !signedIn.isBefore(session.startedAt());
  }

  /**
   * A recent sign-in is not enough on its own when the client asks for a stronger one for links: an
   * ordinary sign-in a minute ago would otherwise let a link through without it. With no values
   * configured, any sign-in passes.
   */
  private boolean hasReachedAcr(Authentication authentication) {
    if (acrValues.isEmpty()) {
      return true;
    }
    return authentication != null
        && authentication.acr() != null
        && acrValues.contains(authentication.acr());
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public boolean hasAcrValues() {
    return !acrValues.isEmpty();
  }
}
