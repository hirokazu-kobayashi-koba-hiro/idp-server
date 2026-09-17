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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.condition.ConditionDefinition;
import org.idp.server.platform.condition.ConditionEvaluator;
import org.idp.server.platform.condition.ConditionMatchMode;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Rules for one kind of self-service contact change (Issue #1416).
 *
 * <p>What the caller must have proven is expressed as conditions over the authentication that
 * produced the access token, not as a named method. A tenant whose users hold a passkey and a
 * tenant whose users hold a password need the same guarantee — "this really is the account holder,
 * recently" — but share no method, so naming one would lock the other out.
 *
 * <p>The condition shape mirrors {@code device_registration_conditions}: {@code any_of} holds
 * groups, a group holds when every condition in it holds, and the rule holds when any group does.
 * Evaluation context:
 *
 * <pre>{@code
 * {
 *   "amr": ["password", "sms"], // as emitted by StandardAuthenticationMethod, not RFC 8176 names
 *   "acr": "urn:...",
 *   "auth_time": 1789000000,  // epoch seconds; absent when the token carries no auth time
 *   "auth_age": 42            // seconds since auth_time; absent when auth_time is
 * }
 * }</pre>
 */
public class ContactChangeRule {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ContactChangeRule.class);

  boolean allowed;
  List<List<ConditionDefinition>> authenticationConditions;

  /**
   * Set when {@code authentication_conditions} was written but could not be read as conditions.
   *
   * <p>Kept rather than silently dropping the unreadable part: dropping only ever makes a rule
   * easier to satisfy, and this rule is the whole of what makes an identifier move heavy. A typo in
   * one operation name would otherwise leave the surviving conditions — or, if nothing survived, no
   * requirement at all — which turns a strict policy permissive without anything saying so.
   */
  boolean authenticationConditionsMalformed;

  /**
   * The {@code authentication_conditions} value exactly as the tenant wrote it.
   *
   * <p>Persistence goes through {@link #toMap()} ({@code Tenant.toMap()} -> {@code
   * identity_policy_config}), so emitting only what parsed would delete an unreadable block on the
   * next write and leave the rule permissive from then on — the read-side refusal would never be
   * reached again. Writing the original back keeps it failing closed, and shows the operator their
   * own typo in the management API response.
   */
  Object rawAuthenticationConditions;

  int maxAuthAgeSeconds;
  boolean notifyPreviousValue;
  IdentityVerifiedBehavior identityVerifiedBehavior;

  public ContactChangeRule(
      boolean allowed,
      List<List<ConditionDefinition>> authenticationConditions,
      int maxAuthAgeSeconds,
      boolean notifyPreviousValue,
      IdentityVerifiedBehavior identityVerifiedBehavior) {
    this(
        allowed,
        authenticationConditions,
        false,
        null,
        maxAuthAgeSeconds,
        notifyPreviousValue,
        identityVerifiedBehavior);
  }

  ContactChangeRule(
      boolean allowed,
      List<List<ConditionDefinition>> authenticationConditions,
      boolean authenticationConditionsMalformed,
      Object rawAuthenticationConditions,
      int maxAuthAgeSeconds,
      boolean notifyPreviousValue,
      IdentityVerifiedBehavior identityVerifiedBehavior) {
    this.allowed = allowed;
    this.authenticationConditions =
        authenticationConditions == null ? new ArrayList<>() : authenticationConditions;
    this.authenticationConditionsMalformed = authenticationConditionsMalformed;
    this.rawAuthenticationConditions = rawAuthenticationConditions;
    this.maxAuthAgeSeconds = maxAuthAgeSeconds;
    this.notifyPreviousValue = notifyPreviousValue;
    this.identityVerifiedBehavior = identityVerifiedBehavior;
  }

  /**
   * Default for a change that moves the tenant's unique key.
   *
   * <p>No authentication condition is imposed by default: the tenant knows which methods its users
   * actually hold, and a default naming any of them would lock out the tenants that do not use it.
   * What is defaulted is the part that is safe to assume — notify the value being replaced, and do
   * not silently keep an eKYC assertion whose basis just moved.
   */
  public static ContactChangeRule defaultIdentifierMoveRule() {
    return new ContactChangeRule(true, new ArrayList<>(), 0, true, IdentityVerifiedBehavior.DENY);
  }

  /** Default for a change that only updates an attribute. */
  public static ContactChangeRule defaultAttributeOnlyRule() {
    return new ContactChangeRule(true, new ArrayList<>(), 0, true, IdentityVerifiedBehavior.ALLOW);
  }

  public boolean isAllowed() {
    return allowed;
  }

  public boolean hasAuthenticationConditions() {
    return authenticationConditions != null && !authenticationConditions.isEmpty();
  }

  /**
   * Whether the authentication behind the token satisfies at least one condition group.
   *
   * <p>No configured condition means no requirement beyond the scope. A condition block that was
   * configured but could not be read is not the same thing: the tenant asked for a requirement, so
   * nothing satisfies it until the configuration is fixed.
   */
  public boolean satisfiedBy(JsonPathWrapper authenticationContext) {
    if (authenticationConditionsMalformed) {
      log.warn(
          "contact_change_policy authentication_conditions could not be read; refusing the change"
              + " rather than applying a weaker rule than the one configured.");
      return false;
    }
    if (!hasAuthenticationConditions()) {
      return true;
    }
    return authenticationConditions.stream()
        .anyMatch(
            group ->
                ConditionEvaluator.evaluate(group, ConditionMatchMode.ALL, authenticationContext));
  }

  public boolean hasMaxAuthAgeSeconds() {
    return maxAuthAgeSeconds > 0;
  }

  /**
   * Upper bound on the age of the authentication, in seconds. Named after OIDC {@code max_age}
   * rather than written as a condition, because "within N seconds" reads poorly as a JSONPath
   * comparison and this is the knob a tenant reaches for.
   */
  public int maxAuthAgeSeconds() {
    return maxAuthAgeSeconds;
  }

  public boolean shouldNotifyPreviousValue() {
    return notifyPreviousValue;
  }

  public IdentityVerifiedBehavior identityVerifiedBehavior() {
    return identityVerifiedBehavior;
  }

  public static ContactChangeRule fromMap(Map<String, Object> map, ContactChangeRule defaultRule) {
    if (map == null || map.isEmpty()) {
      return defaultRule;
    }

    boolean allowed = defaultRule.allowed;
    if (map.get("allowed") instanceof Boolean value) {
      allowed = value;
    }

    int maxAuthAgeSeconds = defaultRule.maxAuthAgeSeconds;
    if (map.get("max_auth_age_seconds") instanceof Number value) {
      maxAuthAgeSeconds = value.intValue();
    }

    boolean notifyPreviousValue = defaultRule.notifyPreviousValue;
    if (map.get("notify_previous_value") instanceof Boolean value) {
      notifyPreviousValue = value;
    }

    IdentityVerifiedBehavior identityVerifiedBehavior = defaultRule.identityVerifiedBehavior;
    if (map.get("identity_verified_behavior") instanceof String value) {
      identityVerifiedBehavior = IdentityVerifiedBehavior.of(value, identityVerifiedBehavior);
    }

    Object rawConditions = map.get("authentication_conditions");
    ParsedConditions parsed = parseConditions(rawConditions);

    return new ContactChangeRule(
        allowed,
        parsed.groups(),
        parsed.malformed(),
        rawConditions,
        maxAuthAgeSeconds,
        notifyPreviousValue,
        identityVerifiedBehavior);
  }

  /** What {@code authentication_conditions} parsed to, and whether any of it was unreadable. */
  private record ParsedConditions(List<List<ConditionDefinition>> groups, boolean malformed) {

    static ParsedConditions absent() {
      return new ParsedConditions(new ArrayList<>(), false);
    }

    static ParsedConditions unreadable() {
      return new ParsedConditions(new ArrayList<>(), true);
    }
  }

  /**
   * Reads {@code {"any_of": [[{path, operation, value}, ...], ...]}}.
   *
   * <p>Never throws: this is tenant configuration read on every request, and a typo must not take
   * the tenant down. It is reported instead, because the alternative — dropping what could not be
   * read — silently relaxes the rule. A group that loses one of two conditions still evaluates, and
   * now passes on half the proof that was asked for; a block where nothing parsed evaluates as "no
   * conditions configured", which passes on none.
   *
   * <p>Absent is not malformed. A tenant that wrote no conditions gets the documented default of no
   * requirement beyond the scope.
   */
  @SuppressWarnings("unchecked")
  private static ParsedConditions parseConditions(Object value) {
    if (value == null) {
      return ParsedConditions.absent();
    }
    if (!(value instanceof Map<?, ?> conditionsMap)) {
      return ParsedConditions.unreadable();
    }
    Object anyOf = ((Map<String, Object>) conditionsMap).get("any_of");
    if (anyOf == null) {
      return ParsedConditions.absent();
    }
    if (!(anyOf instanceof List<?> anyOfList)) {
      return ParsedConditions.unreadable();
    }
    if (anyOfList.isEmpty()) {
      return ParsedConditions.absent();
    }

    List<List<ConditionDefinition>> groups = new ArrayList<>();
    for (Object groupValue : anyOfList) {
      if (!(groupValue instanceof List<?> groupList) || groupList.isEmpty()) {
        return ParsedConditions.unreadable();
      }
      List<ConditionDefinition> group = new ArrayList<>();
      for (Object conditionValue : groupList) {
        ConditionDefinition condition = parseCondition(conditionValue);
        if (condition == null) {
          return ParsedConditions.unreadable();
        }
        group.add(condition);
      }
      groups.add(group);
    }
    return new ParsedConditions(groups, false);
  }

  private static ConditionDefinition parseCondition(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return null;
    }
    Object path = map.get("path");
    Object operation = map.get("operation");
    if (!(path instanceof String pathValue) || !(operation instanceof String operationValue)) {
      return null;
    }
    ConditionOperation conditionOperation = ConditionOperation.from(operationValue);
    if (conditionOperation == ConditionOperation.UNKNOWN) {
      return null;
    }
    return new ConditionDefinition(pathValue, conditionOperation, map.get("value"));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("allowed", allowed);
    if (authenticationConditionsMalformed) {
      // Round-trip what was written. See rawAuthenticationConditions.
      map.put("authentication_conditions", rawAuthenticationConditions);
    } else if (hasAuthenticationConditions()) {
      List<List<Map<String, Object>>> groups = new ArrayList<>();
      for (List<ConditionDefinition> group : authenticationConditions) {
        groups.add(new ArrayList<>(group.stream().map(ConditionDefinition::toMap).toList()));
      }
      Map<String, Object> conditions = new HashMap<>();
      conditions.put("any_of", groups);
      map.put("authentication_conditions", conditions);
    }
    if (hasMaxAuthAgeSeconds()) {
      map.put("max_auth_age_seconds", maxAuthAgeSeconds);
    }
    map.put("notify_previous_value", notifyPreviousValue);
    map.put("identity_verified_behavior", identityVerifiedBehavior.name());
    return map;
  }
}
