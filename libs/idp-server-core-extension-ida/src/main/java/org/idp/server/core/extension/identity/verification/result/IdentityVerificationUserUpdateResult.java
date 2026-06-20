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

package org.idp.server.core.extension.identity.verification.result;

import org.idp.server.core.openid.identity.User;

/**
 * Outcome of {@link IdentityVerificationUserUpdater#update}: the updated {@link User} plus the
 * {@link AppliedUserClaims} that were actually applied, so the caller can both persist the user and
 * record the applied attributes on the verification result (#1607).
 */
public class IdentityVerificationUserUpdateResult {

  User updated;
  AppliedUserClaims applied;

  public IdentityVerificationUserUpdateResult(User updated, AppliedUserClaims applied) {
    this.updated = updated;
    this.applied = applied;
  }

  public User updated() {
    return updated;
  }

  public AppliedUserClaims applied() {
    return applied;
  }
}
