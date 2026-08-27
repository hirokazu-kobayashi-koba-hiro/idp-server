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
 * What to do when the external account being linked is already linked to a different user in the
 * same tenant.
 *
 * <p>This is a tenant decision rather than a database constraint. The stored external account
 * identifier is not an identity — nothing authenticates through it — so forbidding duplicates
 * outright would block legitimate cases such as a shared corporate account, while giving whoever
 * links first the power to keep the owner out.
 */
public enum DuplicateLinkPolicy {
  /** Refuse the link. The default, so an unset configuration lands on the cautious side. */
  REJECT,
  /** Allow several users to hold their own link to the same external account. */
  ALLOW;

  public static DuplicateLinkPolicy of(String value) {
    if (value == null || value.isEmpty()) {
      return REJECT;
    }
    return switch (value.toLowerCase()) {
      case "allow" -> ALLOW;
      case "reject" -> REJECT;
      default -> REJECT;
    };
  }

  public boolean isReject() {
    return this == REJECT;
  }
}
