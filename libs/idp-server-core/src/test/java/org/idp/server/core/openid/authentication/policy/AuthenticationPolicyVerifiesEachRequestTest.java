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

import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Issue #1907: a policy that checks each authorization request on its own is not satisfied by a
 * session alone (prompt=none).
 */
class AuthenticationPolicyVerifiesEachRequestTest {

  // JSON is written with ` for " so that CsvSource, which quotes with ', reads it as it is.

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        // an attribute verification step
        "{`step_definitions`:[{`method`:`attribute-verification`,`interaction`:`kba`,`order`:2}]}|true",
        // a condition on what the request asked for
        "{`success_conditions`:{`any_of`:[[{`path`:`$.request.custom_params.member_no`,`operation`:`exists`,`value`:true}]]}}|true",
        // a value_path into the request
        "{`success_conditions`:{`any_of`:[[{`path`:`$.user.custom_properties.member_no`,`operation`:`eq`,`value_path`:`$.request.custom_params.member_no`}]]}}|true",
        // a condition on a verification`s result
        "{`lock_conditions`:{`any_of`:[[{`path`:`$.attribute-verification.interactions.kba.failure_count`,`operation`:`gte`,`value`:5}]]}}|true",
        // authentication only
        "{`step_definitions`:[{`method`:`password`,`order`:1}],`success_conditions`:{`any_of`:[[{`path`:`$.password-authentication.success_count`,`operation`:`gte`,`value`:1}]]}}|false",
        "{}|false",
      })
  void verifiesEachRequest(String json, boolean expected) {
    AuthenticationPolicy policy =
        jsonConverter.read(json.replace('`', '"'), AuthenticationPolicy.class);

    assertEquals(expected, policy.verifiesEachRequest());
  }
}
