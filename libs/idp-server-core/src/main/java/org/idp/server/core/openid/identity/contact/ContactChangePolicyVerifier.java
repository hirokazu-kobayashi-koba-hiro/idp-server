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

package org.idp.server.core.openid.identity.contact;

import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.ContactChangeRule;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;

/**
 * Applies the tenant's {@code contact_change_policy} to one operation (Issue #1416).
 *
 * <p>Runs at both ends of the flow. At request time it decides whether a code may be sent at all —
 * a rule the caller cannot satisfy must not cause a message to a recipient of their choosing. At
 * commit time it runs again, because the account's verification state and the token's
 * authentication age can both have moved while the challenge was outstanding.
 *
 * <p>{@code allowed} applies to changes only (Issue #1895). The authentication conditions and
 * {@code max_auth_age} apply to both: a verification still writes {@code *_verified}, which the
 * tenant may reasonably want behind a recent or stronger authentication.
 */
public class ContactChangePolicyVerifier {

  /**
   * @return the rejection to return to the caller, or null when the operation may proceed
   */
  public static ContactVerificationResponse verify(
      Tenant tenant,
      User user,
      ContactVerificationOperation operation,
      ContactChangeAuthenticationContext authenticationContext) {

    TenantIdentityPolicy policy = tenant.identityPolicyConfig();
    ContactChangeRule rule = ruleFor(policy, operation);

    // Issue #1895: allowed gates changes only. A verification moves no value — it sets
    // {@code *_verified} on the address the account already holds — so "this tenant does not allow
    // changing this contact" is not a decision about it. Without this guard a verification is
    // evaluated against attributeOnly() regardless, because movesIdentifier() is `change && ...`,
    // and a tenant that switched attribute_only.allowed off lost the ability to verify an address
    // it never intended to make changeable. On USERNAME / EXTERNAL_USER_ID tenants both channels
    // resolve to attributeOnly(), so that took out the whole feature.
    if (operation.isChange() && !rule.isAllowed()) {
      return ContactVerificationResponse.failure(
          "this tenant does not allow changing this contact.", operation);
    }

    if (!rule.satisfiedBy(authenticationContext.toJsonPath())) {
      return ContactVerificationResponse.failure(
          "this change requires a stronger authentication than the one behind this token.",
          operation);
    }

    if (!authenticationContext.withinMaxAuthAge(rule.maxAuthAgeSeconds())) {
      return ContactVerificationResponse.failure(
          "this change requires a recent authentication; sign in again and retry.", operation);
    }

    return null;
  }

  /** The rule for this operation, or the permissive verify rule when nothing is being replaced. */
  public static ContactChangeRule ruleFor(
      TenantIdentityPolicy policy, ContactVerificationOperation operation) {
    return policy.contactChangePolicy().ruleFor(operation.movesIdentifier(policy));
  }
}
