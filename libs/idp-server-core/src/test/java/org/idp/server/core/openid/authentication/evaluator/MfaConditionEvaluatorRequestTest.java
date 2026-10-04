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

package org.idp.server.core.openid.authentication.evaluator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationContext;
import org.idp.server.core.openid.authentication.AuthenticationCustomParams;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.CustomParamSource;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Issue #1907: the custom parameters of the authorization request as {@code
 * $.request.custom_params.*}, limited to the sources the policy trusts, and {@code value_path}.
 */
class MfaConditionEvaluatorRequestTest {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private static final String MEMBER_MATCHES =
      "[[{\"path\":\"$.request.custom_params.member_no\",\"operation\":\"eq\","
          + "\"value_path\":\"$.user.custom_properties.member_no\"}]]";

  @Test
  void matchesAValueFromATrustedSource() {
    Map<String, Object> request =
        request(Map.of("member_no", "A123"), CustomParamSource.REQUEST_OBJECT, policy(null));

    assertTrue(satisfied(MEMBER_MATCHES, userWithMember("A123"), request));
    assertFalse(satisfied(MEMBER_MATCHES, userWithMember("B456"), request));
  }

  @Test
  void hidesAValueFromTheQueryByDefault() {
    Map<String, Object> request =
        request(Map.of("member_no", "A123"), CustomParamSource.QUERY, policy(null));

    assertEquals(Map.of(), request);
    assertFalse(satisfied(MEMBER_MATCHES, userWithMember("A123"), request));
  }

  @Test
  void showsAValueFromTheQueryWhenThePolicyTrustsIt() {
    Map<String, Object> request =
        request(Map.of("member_no", "A123"), CustomParamSource.QUERY, policy("[\"query\"]"));

    assertTrue(satisfied(MEMBER_MATCHES, userWithMember("A123"), request));
  }

  @Test
  void hidesAPushedValueWhenThePolicyTrustsOnlyRequestObjects() {
    Map<String, Object> request =
        request(
            Map.of("member_no", "A123"), CustomParamSource.PUSHED, policy("[\"request_object\"]"));

    assertFalse(satisfied(MEMBER_MATCHES, userWithMember("A123"), request));
  }

  /** Two missing values are not equal, unlike plain {@code eq}. */
  @ParameterizedTest
  @CsvSource({
    // actual, expected, eq,    ne
    "A123,     A123,     true,  false",
    "A123,     B456,     false, true",
    ",         A123,     false, true",
    "A123,     ,         false, true",
    ",         ,         false, true",
  })
  void comparesTwoPaths(String actual, String expected, boolean eq, boolean ne) {
    assertEquals(eq, MfaConditionEvaluator.isPathComparisonSatisfied(actual, "eq", expected));
    assertEquals(ne, MfaConditionEvaluator.isPathComparisonSatisfied(actual, "ne", expected));
  }

  @Test
  void otherOperationsDoNotHoldWithAValuePath() {
    assertFalse(MfaConditionEvaluator.isPathComparisonSatisfied("A", "contains", "A"));
  }

  @Test
  void neitherARequestValueNorAnAttributeIsNotAMatch() {
    Map<String, Object> request = request(Map.of(), CustomParamSource.PUSHED, policy(null));

    assertFalse(
        satisfied(
            MEMBER_MATCHES, new User().setSub("u").setStatus(UserStatus.REGISTERED), request));
  }

  private boolean satisfied(String anyOf, User user, Map<String, Object> request) {
    AuthenticationResultConditionConfig config =
        jsonConverter.read("{\"any_of\":" + anyOf + "}", AuthenticationResultConditionConfig.class);
    return MfaConditionEvaluator.isSuccessSatisfied(config, results(), user, request);
  }

  private static Map<String, Object> request(
      Map<String, String> values, CustomParamSource source, AuthenticationPolicy policy) {
    boolean pushed = source == CustomParamSource.PUSHED;
    AuthenticationCustomParams customParams =
        AuthenticationCustomParams.of(CustomParams.of(values, source), pushed, true, true);
    AuthenticationContext context =
        new AuthenticationContext(
            new AcrValues(),
            new Scopes(),
            new BindingMessage(),
            new AuthorizationDetails(),
            customParams);
    return PolicyEvaluationRequestContextCreator.create(context, policy);
  }

  private static AuthenticationPolicy policy(String trustedSources) {
    String json =
        trustedSources == null
            ? "{}"
            : "{\"custom_params_trusted_sources\":" + trustedSources + "}";
    return jsonConverter.read(json, AuthenticationPolicy.class);
  }

  private static User userWithMember(String memberNo) {
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put("member_no", memberNo);
    return new User()
        .setSub("u")
        .setStatus(UserStatus.REGISTERED)
        .setCustomProperties(customProperties);
  }

  private static AuthenticationInteractionResults results() {
    Map<String, AuthenticationInteractionResult> values = new HashMap<>();
    values.put(
        "password-authentication",
        new AuthenticationInteractionResult(
            "authentication", "password", 1, 1, 0, LocalDateTime.now()));
    return new AuthenticationInteractionResults(values);
  }
}
