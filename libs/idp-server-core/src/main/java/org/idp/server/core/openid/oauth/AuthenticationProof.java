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

package org.idp.server.core.openid.oauth;

import org.idp.server.platform.json.JsonReadable;

/**
 * Proof that this browser is the one that authenticated.
 *
 * <p>Travels as {@code auth_proof}: handed to the browser that supplied the credentials, and
 * required back from it before the authorization code is minted or delivered.
 *
 * <h2>Why a value and not a cookie</h2>
 *
 * <p>Where the authorization view is on another site, every step between the authorization request
 * and the redirect back runs as third-party XHR, and this server's cookies are neither sent nor
 * kept. Nothing the browser holds in a cookie can be relied on, so what distinguishes one browser
 * from another has to be a value the view itself carries.
 *
 * <p>The browser binding cookie could not do this job even where it survives, because it answers
 * the wrong question. It says "is this the browser that <i>started</i> the request?", and in the
 * attack this defends against that is the attacker's browser: he opens a request, sends the view's
 * URL to a victim, the victim authenticates, and the attacker holds the only cookie. Acting on it
 * hands him a session as the victim.
 *
 * <p>The question that has to be answered is "is this the browser that <i>authenticated</i>?", and
 * only one thing separates the two browsers there — the victim supplied credentials and the
 * attacker cannot. So this value is issued the moment the transaction succeeds, handed back in that
 * response, and required from then on.
 *
 * <h2>Rotation</h2>
 *
 * <p>It is issued twice, and each one is consumed on use:
 *
 * <ol>
 *   <li>at authentication success, with no redirect yet — this is what {@code /authorize} requires,
 *       so an attacker who knows the request id cannot mint a code from it
 *   <li>at {@code /authorize}, carrying the redirect the code went into — this is what {@code
 *       /complete} requires, so the code can only be delivered to the same browser
 * </ol>
 */
public class AuthenticationProof implements JsonReadable {

  String authorizationRequestId;
  String redirectUri;

  public AuthenticationProof() {}

  public AuthenticationProof(String authorizationRequestId, String redirectUri) {
    this.authorizationRequestId = authorizationRequestId;
    this.redirectUri = redirectUri;
  }

  public String authorizationRequestId() {
    return authorizationRequestId;
  }

  public String redirectUri() {
    return redirectUri;
  }

  /**
   * Whether this is the second of the two: the one that carries the redirect for {@code /complete}.
   */
  public boolean hasRedirectUri() {
    return redirectUri != null && !redirectUri.isEmpty();
  }

  /** Whether this proof is the one issued for {@code authorizationRequestIdentifier}. */
  public boolean issuedFor(String authorizationRequestIdentifier) {
    return authorizationRequestId != null
        && authorizationRequestId.equals(authorizationRequestIdentifier);
  }

  public static String cacheKey(String tenantId, String authProof) {
    return String.format("authentication_proof:%s:%s", tenantId, authProof);
  }
}
