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

package org.idp.server.core.openid.clientinstance;

import java.util.Objects;

/**
 * How a client instance registration must be backed.
 *
 * <ul>
 *   <li>{@link #user_bound} — the registration is authenticated by an ID token this Authorization
 *       Server issued, and the instance is bound to that user. The ID token carries {@code nonce =
 *       request_hash}, so it only authenticates the registration of the key it was obtained for
 *   <li>{@link #undefined} — not configured or unknown; registration is rejected rather than
 *       falling back to a weaker policy
 * </ul>
 *
 * <p>Registration that is not tied to a user is not offered: clients whose instances serve several
 * users (shared terminals) or whose attester is another party (wallets) use the {@code
 * attester_jwks} or {@code x5c} trust sources instead of registered instance keys.
 */
public enum ClientInstanceRegistrationPolicy {
  user_bound,
  undefined;

  public static ClientInstanceRegistrationPolicy of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientInstanceRegistrationPolicy policy : ClientInstanceRegistrationPolicy.values()) {
      if (policy.name().equals(value)) {
        return policy;
      }
    }
    return undefined;
  }

  public boolean isUserBound() {
    return this == user_bound;
  }

  public boolean isUndefined() {
    return this == undefined;
  }
}
