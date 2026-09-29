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

package org.idp.server.core.openid.oauth.repository;

import java.util.Optional;
import org.idp.server.core.openid.oauth.AuthenticationProof;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Where an {@link AuthenticationProof} lives between the step that earned it and the step that
 * spends it.
 *
 * <p>Bounded in time and single use, which is why it is kept behind this interface rather than
 * written wherever it is needed: both properties are easy to state and easy to lose. Claiming has
 * to be atomic, or two requests arriving together both succeed; and a proof has to disappear once
 * spent, or it is not single use at all. Keeping issue and claim next to each other is what makes
 * those two readable as one rule.
 *
 * <p>It also keeps the other thing a cross-site flow loses with its cookies: which OP session the
 * authorization belongs to. That is not part of the proof, because the step that creates the
 * session is often not the one that issues a proof — a device completing the last step creates the
 * session, and nothing it is handed ever reaches the browser. So the session is bound to the
 * authorization request instead, where every later step can find it no matter who created it.
 */
public interface AuthenticationProofRepository {

  /**
   * Issues the proof the browser presents to {@code /authorize}.
   *
   * <p>Lives as long as the authorization request. A device step can follow the one that issued it,
   * and the browser has no way to earn another while it waits; a lifetime of its own would strand
   * anyone slower than it.
   *
   * @return the opaque value handed to the browser
   */
  String issue(Tenant tenant, AuthorizationRequest authorizationRequest, String sub);

  /**
   * Issues the proof the browser presents to {@code /complete}, carrying the redirect the code went
   * into.
   *
   * @return the opaque value handed to the browser
   */
  String issueForCompletion(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      String sub,
      String redirectUri);

  /**
   * Takes the proof, so that it can be spent once and only once.
   *
   * @return the proof, or null when there is none to claim — already spent, never issued, or the
   *     store could not answer. All three are the same answer to the caller: do not continue.
   */
  AuthenticationProof claim(Tenant tenant, String value);

  /**
   * Records the OP session the authentication created, for as long as the authorization request
   * lives.
   */
  void bindSession(
      Tenant tenant, AuthorizationRequest authorizationRequest, OPSessionIdentifier opSessionId);

  /** The OP session bound to this authorization request, if one was created. */
  Optional<OPSessionIdentifier> findSession(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier);
}
