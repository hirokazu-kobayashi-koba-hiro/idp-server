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

package org.idp.server.platform.multi_tenancy.tenant.policy;

/**
 * What a self-service contact change may do to an identity-verified account (Issue #1416).
 *
 * <p>An account that went through eKYC carries claims asserted by an external provider. Moving the
 * login identifier afterwards is not the same act as updating a plain attribute, so the tenant
 * decides which of the three it is.
 */
public enum IdentityVerifiedBehavior {
  /** The verification state is irrelevant to this change. */
  ALLOW,

  /**
   * Refuse while the account is identity verified, or while verification is required. This is the
   * line {@code IdentityVerificationUserUpdater} already draws for verification results.
   */
  DENY,

  /** Allow, but drop the verified state on commit, because its basis no longer holds. */
  DOWNGRADE_STATUS;

  public static IdentityVerifiedBehavior of(String value, IdentityVerifiedBehavior defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    for (IdentityVerifiedBehavior behavior : values()) {
      if (behavior.name().equalsIgnoreCase(value)) {
        return behavior;
      }
    }
    return defaultValue;
  }

  public boolean isDeny() {
    return this == DENY;
  }

  public boolean isDowngradeStatus() {
    return this == DOWNGRADE_STATUS;
  }
}
