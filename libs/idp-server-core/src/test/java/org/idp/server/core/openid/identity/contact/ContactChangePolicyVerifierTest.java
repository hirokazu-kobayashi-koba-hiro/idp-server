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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code contact_change_policy} decides what a caller may do to a contact, and the two things it
 * can be asked about are not the same privilege: a change replaces the value on the account, a
 * verification only records that the value already there was reachable.
 *
 * <p>Issue #1895: {@code allowed} was applied to both, because {@link
 * ContactVerificationOperation#movesIdentifier} is {@code change && ...} — so a verification always
 * resolves to {@code attribute_only}, and a tenant that turned {@code attribute_only.allowed} off
 * to stop attribute changes silently lost verification too. On a {@code USERNAME} tenant both
 * channels resolve to {@code attribute_only}, so that was the whole feature.
 */
class ContactChangePolicyVerifierTest {

  /** A tenant whose unique key is the username, so neither channel moves the login identifier. */
  private static Tenant usernameTenant(Map<String, Object> contactChangePolicy) {
    return tenant("USERNAME", contactChangePolicy);
  }

  /** A tenant whose unique key is the email, so changing email moves the login identifier. */
  private static Tenant emailTenant(Map<String, Object> contactChangePolicy) {
    return tenant("EMAIL", contactChangePolicy);
  }

  private static Tenant tenant(String uniqueKeyType, Map<String, Object> contactChangePolicy) {
    TenantIdentityPolicy policy =
        TenantIdentityPolicy.fromMap(
            Map.of(
                "identity_unique_key_type",
                uniqueKeyType,
                "contact_change_policy",
                contactChangePolicy));
    return new Tenant(
        new TenantIdentifier("1e68932e-ed4a-43e7-b412-460665e42df3"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        policy,
        null,
        true);
  }

  private static ContactChangeAuthenticationContext passwordAuthenticatedNow() {
    Authentication authentication =
        new Authentication().setTime(LocalDateTime.now()).addMethods(List.of("password"));
    return new ContactChangeAuthenticationContext(authentication, new User());
  }

  /** A token that carries no record of when or how its holder authenticated. */
  private static ContactChangeAuthenticationContext noAuthenticationRecorded() {
    return new ContactChangeAuthenticationContext(new Authentication(), new User());
  }

  private static String errorDescription(ContactVerificationResponse response) {
    return (String) response.contents().get("error_description");
  }

  @Test
  @DisplayName("attribute_only.allowed=false は verify を拒否しない (Issue #1895)")
  void attributeOnlyNotAllowedStillPermitsVerify() {
    Tenant tenant = usernameTenant(Map.of("attribute_only", Map.of("allowed", false)));

    assertNull(
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_VERIFY,
            passwordAuthenticatedNow()));
    assertNull(
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.PHONE_VERIFY,
            passwordAuthenticatedNow()));
  }

  @Test
  @DisplayName("attribute_only.allowed=false は change を拒否する")
  void attributeOnlyNotAllowedRejectsChange() {
    Tenant tenant = usernameTenant(Map.of("attribute_only", Map.of("allowed", false)));

    ContactVerificationResponse response =
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_CHANGE,
            passwordAuthenticatedNow());

    assertNotNull(response);
    assertEquals(400, response.statusCode());
    assertEquals("this tenant does not allow changing this contact.", errorDescription(response));
  }

  @Test
  @DisplayName("identifier_move.allowed=false でも verify は通り、change だけ拒否される")
  void identifierMoveNotAllowedRejectsChangeOnly() {
    Tenant tenant = emailTenant(Map.of("identifier_move", Map.of("allowed", false)));

    assertNull(
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_VERIFY,
            passwordAuthenticatedNow()));

    ContactVerificationResponse response =
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_CHANGE,
            passwordAuthenticatedNow());

    assertNotNull(response);
    assertEquals("this tenant does not allow changing this contact.", errorDescription(response));
  }

  @Test
  @DisplayName("authentication_conditions は verify にも掛かる")
  void authenticationConditionsStillApplyToVerify() {
    Tenant tenant =
        usernameTenant(
            Map.of(
                "attribute_only",
                Map.of(
                    "allowed",
                    true,
                    "authentication_conditions",
                    Map.of(
                        "any_of",
                        List.of(
                            List.of(
                                Map.of(
                                    "path",
                                    "$.amr",
                                    "operation",
                                    "contains",
                                    "value",
                                    "fido-uaf")))))));

    ContactVerificationResponse response =
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_VERIFY,
            passwordAuthenticatedNow());

    assertNotNull(response);
    assertEquals(
        "this change requires a stronger authentication than the one behind this token.",
        errorDescription(response));
  }

  @Test
  @DisplayName("max_auth_age_seconds は verify にも掛かる。allowed=false でも素通りしない")
  void maxAuthAgeStillAppliesToVerifyWhenNotAllowed() {
    Tenant tenant =
        usernameTenant(
            Map.of("attribute_only", Map.of("allowed", false, "max_auth_age_seconds", 300)));

    ContactVerificationResponse response =
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_VERIFY,
            noAuthenticationRecorded());

    assertNotNull(response);
    assertEquals(
        "this change requires a recent authentication; sign in again and retry.",
        errorDescription(response));
  }

  @Test
  @DisplayName("既定ポリシー（未設定）は verify も change も通す")
  void defaultPolicyPermitsBoth() {
    Tenant tenant = usernameTenant(Map.of());

    assertNull(
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_VERIFY,
            passwordAuthenticatedNow()));
    assertNull(
        ContactChangePolicyVerifier.verify(
            tenant,
            new User(),
            ContactVerificationOperation.EMAIL_CHANGE,
            passwordAuthenticatedNow()));
  }
}
