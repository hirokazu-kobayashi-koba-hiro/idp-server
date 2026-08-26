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

/**
 * Lifecycle of an {@link AccountLinkingSession}.
 *
 * <p>The linking flow crosses an unauthenticated boundary: the external IdP returns to a callback
 * that carries no Bearer token. Splitting the flow into explicit states keeps that callback from
 * being able to finalize anything on its own (park-and-claim).
 *
 * <pre>
 *   PENDING ---- /linking/start (operator verified) ----&gt; AUTHORIZED
 *   AUTHORIZED - /linking/callback (code exchanged) ----&gt; PARKED
 *   PARKED ----- complete (Bearer verified) -----------&gt; CONSUMED
 * </pre>
 */
public enum AccountLinkingSessionStatus {
  PENDING,
  AUTHORIZED,
  PARKED,
  CONSUMED;

  /** Returns whether this status may transition into {@code next}. */
  public boolean canTransitionTo(AccountLinkingSessionStatus next) {
    return switch (this) {
      case PENDING -> next == AUTHORIZED;
      case AUTHORIZED -> next == PARKED;
      case PARKED -> next == CONSUMED;
      case CONSUMED -> false;
    };
  }

  public boolean isPending() {
    return this == PENDING;
  }

  public boolean isAuthorized() {
    return this == AUTHORIZED;
  }

  public boolean isParked() {
    return this == PARKED;
  }

  public boolean isConsumed() {
    return this == CONSUMED;
  }
}
