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

import org.idp.server.platform.log.LoggerWrapper;

/**
 * What a self-service contact change may do to an identity-verified account (Issue #1416).
 *
 * <p>An account that went through eKYC carries claims asserted by an external provider. Moving the
 * login identifier afterwards is not the same act as updating a plain attribute, so the tenant
 * decides which of the two it is.
 *
 * <p>A third option — allow the change and drop the verified state on commit — is deliberately
 * <em>not</em> here. It was, briefly, and nothing acted on it: the only question asked at commit
 * time is {@link #isDeny()}, so a tenant naming it got the permissive branch and kept {@code
 * IDENTITY_VERIFIED} across an identifier move, which is exactly what this enum exists to prevent.
 * An unimplemented option that reads as the strictest and behaves as the loosest is worse than no
 * option, so it stays out until something downgrades the status.
 */
public enum IdentityVerifiedBehavior {
  /** The verification state is irrelevant to this change. */
  ALLOW,

  /**
   * Refuse while the account is identity verified, or while verification is required. This is the
   * line {@code IdentityVerificationUserUpdater} already draws for verification results.
   */
  DENY;

  private static final LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerifiedBehavior.class);

  /**
   * Resolves the configured value, falling back when it names nothing this version implements.
   *
   * <p>The fallback is the caller's default, which is {@code DENY} for a change that moves the
   * login identifier — so a typo, or a value from a future version, lands on the strict side rather
   * than the permissive one.
   */
  public static IdentityVerifiedBehavior of(String value, IdentityVerifiedBehavior defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    for (IdentityVerifiedBehavior behavior : values()) {
      if (behavior.name().equalsIgnoreCase(value)) {
        return behavior;
      }
    }
    log.warn(
        "Unsupported identity_verified_behavior ({}); falling back to {}.", value, defaultValue);
    return defaultValue;
  }

  public boolean isDeny() {
    return this == DENY;
  }
}
