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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.junit.jupiter.api.Test;

/**
 * Issue #1907: on session reuse, verifications are not carried over; the ones checked again for the
 * new request are counted in instead.
 */
class AuthenticationInteractionResultsSessionReuseTest {

  private static final String STEP_PASSED =
      "{\"any_of\":[[{\"path\":\"$.attribute-verification.interactions.member-match.success_count\","
          + "\"operation\":\"gte\",\"value\":1}]]}";

  @Test
  void dropsVerificationsAndKeepsAuthentications() {
    AuthenticationInteractionResults session = sessionResults();

    AuthenticationInteractionResults reused = session.withoutVerifications();

    assertTrue(reused.contains("password-authentication"));
    assertFalse(reused.contains("attribute-verification"));
  }

  @Test
  void countsInAVerificationCheckedAgainUnderItsInteraction() {
    AuthenticationInteractionResults reused =
        sessionResults().withoutVerifications().updatedWith(recheckedMemberMatch());

    assertFalse(satisfied(sessionResults().withoutVerifications()));
    assertTrue(satisfied(reused));
  }

  private static boolean satisfied(AuthenticationInteractionResults results) {
    AuthenticationResultConditionConfig config =
        JsonConverter.snakeCaseInstance()
            .read(STEP_PASSED, AuthenticationResultConditionConfig.class);
    return MfaConditionEvaluator.isSatisfied(config, results, User.notFound());
  }

  private static AuthenticationInteractionResults sessionResults() {
    Map<String, AuthenticationInteractionResult> values = new HashMap<>();
    values.put(
        "password-authentication",
        new AuthenticationInteractionResult(
            "AUTHENTICATION", "password", 1, 1, 0, LocalDateTime.now()));
    values.put(
        "attribute-verification",
        new AuthenticationInteractionResult(
            "VERIFICATION",
            "attribute-verification",
            1,
            1,
            0,
            LocalDateTime.now(),
            Map.of(
                "member-match",
                new AuthenticationInteractionResult(
                    "VERIFICATION", "attribute-verification", 1, 1, 0, LocalDateTime.now()))));
    return new AuthenticationInteractionResults(values);
  }

  private static AuthenticationInteractionRequestResult recheckedMemberMatch() {
    AuthenticationInteractionRequestResult result =
        new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.SUCCESS,
            StandardAuthenticationInteraction.ATTRIBUTE_VERIFICATION.toType(),
            OperationType.VERIFICATION,
            "attribute-verification",
            new User().setSub("u"),
            Map.of(),
            DefaultSecurityEventType.attribute_verification_success);
    result.setInteractionName("member-match");
    return result;
  }
}
