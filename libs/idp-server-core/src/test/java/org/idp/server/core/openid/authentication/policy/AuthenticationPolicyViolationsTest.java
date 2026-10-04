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

package org.idp.server.core.openid.authentication.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/** Issue #1907: what the management API refuses in an authentication policy. */
class AuthenticationPolicyViolationsTest {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  void acceptsValuePathOnEqAndNe() {
    assertEquals(
        List.of(),
        violationsOf(
            "{\"success_conditions\":{\"any_of\":[["
                + "{\"path\":\"$.request.custom_params.a\",\"operation\":\"eq\",\"value_path\":\"$.user.sub\"},"
                + "{\"path\":\"$.request.custom_params.b\",\"operation\":\"ne\",\"value_path\":\"$.user.sub\"}"
                + "]]},\"custom_params_trusted_sources\":[\"pushed\",\"request_object\",\"query\"]}"));
  }

  @Test
  void refusesValuePathOnOtherOperations() {
    assertEquals(
        1,
        violationsOf(
                "{\"lock_conditions\":{\"any_of\":[[{\"path\":\"$.a\",\"operation\":\"gte\",\"value_path\":\"$.b\"}]]}}")
            .size());
  }

  @Test
  void refusesValuePathTogetherWithValue() {
    assertEquals(
        1,
        violationsOf(
                "{\"failure_conditions\":{\"any_of\":[[{\"path\":\"$.a\",\"operation\":\"eq\",\"value\":\"x\",\"value_path\":\"$.b\"}]]}}")
            .size());
  }

  @Test
  void refusesAnUnknownSource() {
    assertEquals(1, violationsOf("{\"custom_params_trusted_sources\":[\"header\"]}").size());
  }

  @Test
  void readsConditionsWrittenAsNull() {
    assertEquals(
        List.of(),
        violationsOf(
            "{\"success_conditions\":null,\"failure_conditions\":null,"
                + "\"lock_conditions\":null,\"device_registration_conditions\":null}"));
  }

  @Test
  void trustsPushedAndRequestObjectByDefault() {
    AuthenticationPolicy policy = jsonConverter.read("{}", AuthenticationPolicy.class);

    assertEquals(List.of("pushed", "request_object"), policy.customParamsTrustedSources());
    assertFalse(policy.toMap().containsKey("custom_params_trusted_sources"));
  }

  private static List<String> violationsOf(String json) {
    return jsonConverter.read(json, AuthenticationPolicy.class).violations();
  }
}
