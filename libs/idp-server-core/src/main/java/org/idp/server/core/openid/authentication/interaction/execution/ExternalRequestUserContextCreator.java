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

package org.idp.server.core.openid.authentication.interaction.execution;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRole;

/**
 * Creates the allow-listed authenticated-user projection injected into the mapping source of an
 * external authentication HTTP request (consumed via JsonPath as {@code $.user.*}, the same prefix
 * the policy-condition context uses in Issue #1501).
 *
 * <p><b>Issue #1439:</b> lets an authentication interaction forward the authenticated user's
 * attributes to an external API (e.g. a risk/fraud endpoint that needs to know <em>whose</em> risk
 * is being scored) through the config's {@code body_mapping_rules}, without the relying party
 * having to resend them.
 *
 * <p><b>Why this is separate from {@link
 * org.idp.server.core.openid.authentication.evaluator.PolicyEvaluationUserContextCreator}:</b> that
 * projection is scoped to <em>in-process</em> policy evaluation — a boolean oracle — and its
 * Javadoc states it must never be sent to external endpoints. This projection is the deliberate
 * external-egress counterpart and has a different risk calculus: values here leave the process and
 * reach a third party. It is therefore a distinct <b>allow list</b> tuned for what an external
 * authentication API legitimately needs, and it is fail-safe: a newly added {@link User} field
 * stays hidden until it is deliberately added here.
 *
 * <p><b>What is exposed:</b> {@code sub}, {@code provider_id}, the {@code email} / {@code
 * phone_number} identifiers, the {@code name} plus its {@code given_name} / {@code family_name} /
 * {@code middle_name} parts (so a fraud API that wants split name fields can be fed), {@code roles}
 * (as names) and tenant-managed {@code custom_properties}. Egress is still opt-in per field: an
 * attribute only leaves the process if the tenant admin writes a mapping rule for it.
 *
 * <p><b>What is never exposed:</b> credentials and secrets ({@code hashed_password}, {@code
 * credentials}), {@code verified_claims} (regulated identity-verification data), and
 * evaluation-only signals ({@code status}, {@code has_password}, {@code permissions}) that carry no
 * value for an external call.
 */
public class ExternalRequestUserContextCreator {

  /**
   * @return allow-listed user attributes for external request egress, or an empty map when the user
   *     is null or does not exist
   */
  public static Map<String, Object> create(User user) {
    Map<String, Object> map = new HashMap<>();
    if (user == null || !user.exists()) {
      return map;
    }
    map.put("sub", user.sub());
    map.put("provider_id", user.providerId());
    if (user.hasEmail()) map.put("email", user.email());
    if (user.hasPhoneNumber()) map.put("phone_number", user.phoneNumber());
    if (user.hasName()) map.put("name", user.name());
    if (user.hasGivenName()) map.put("given_name", user.givenName());
    if (user.hasFamilyName()) map.put("family_name", user.familyName());
    if (user.hasMiddleName()) map.put("middle_name", user.middleName());
    if (user.hasRoles()) map.put("roles", user.roles().stream().map(UserRole::roleName).toList());
    if (user.hasCustomProperties())
      map.put("custom_properties", new HashMap<>(user.customPropertiesValue()));
    return map;
  }
}
