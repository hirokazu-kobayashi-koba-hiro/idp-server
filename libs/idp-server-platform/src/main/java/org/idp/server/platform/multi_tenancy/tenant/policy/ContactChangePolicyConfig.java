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

import java.util.HashMap;
import java.util.Map;

/**
 * Tenant policy for self-service contact changes (Issue #1416).
 *
 * <p>Split by whether the change moves the tenant's unique key, because those are different acts.
 * Replacing an attribute the tenant does not key on updates a claim; replacing the one it does key
 * on moves the login identifier, and afterwards the previous value no longer signs in. Under {@code
 * unique_key_type: EMAIL} an email change is the second and a phone change the first, so the
 * distinction cannot be drawn from the channel alone — only this policy knows which is which.
 *
 * <p>That asymmetry is already recognised elsewhere: {@code
 * IdentityVerificationUserUpdater.PATCHABLE_STANDARD_CLAIMS} lets a verification result patch
 * {@code email} and {@code phone_number} but deliberately refuses {@code preferred_username},
 * "identifiers and lifecycle state must never be patchable ... even by tenant configuration".
 */
public class ContactChangePolicyConfig {

  ContactChangeRule identifierMove;
  ContactChangeRule attributeOnly;

  public ContactChangePolicyConfig(
      ContactChangeRule identifierMove, ContactChangeRule attributeOnly) {
    this.identifierMove = identifierMove;
    this.attributeOnly = attributeOnly;
  }

  public static ContactChangePolicyConfig defaultPolicy() {
    return new ContactChangePolicyConfig(
        ContactChangeRule.defaultIdentifierMoveRule(),
        ContactChangeRule.defaultAttributeOnlyRule());
  }

  /**
   * The rule governing a change, chosen by whether it moves the unique key.
   *
   * @param movesIdentifier whether committing this change relocates {@code preferred_username}
   */
  public ContactChangeRule ruleFor(boolean movesIdentifier) {
    return movesIdentifier ? identifierMove : attributeOnly;
  }

  public ContactChangeRule identifierMove() {
    return identifierMove;
  }

  public ContactChangeRule attributeOnly() {
    return attributeOnly;
  }

  @SuppressWarnings("unchecked")
  public static ContactChangePolicyConfig fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultPolicy();
    }
    ContactChangeRule identifierMove =
        ContactChangeRule.fromMap(
            asMap(map.get("identifier_move")), ContactChangeRule.defaultIdentifierMoveRule());
    ContactChangeRule attributeOnly =
        ContactChangeRule.fromMap(
            asMap(map.get("attribute_only")), ContactChangeRule.defaultAttributeOnlyRule());
    return new ContactChangePolicyConfig(identifierMove, attributeOnly);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("identifier_move", identifierMove.toMap());
    map.put("attribute_only", attributeOnly.toMap());
    return map;
  }
}
