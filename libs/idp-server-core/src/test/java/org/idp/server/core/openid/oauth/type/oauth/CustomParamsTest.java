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

package org.idp.server.core.openid.oauth.type.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.request.RequestObjectParameters;
import org.junit.jupiter.api.Test;

/** Issue #1907: custom parameters of a request object, and where each key came from. */
class CustomParamsTest {

  @Test
  void requestObjectTakesItsExtensionClaimsAndLeavesOutStandardParametersAndJwtClaims() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("client_id", "client");
    payload.put("scope", "openid");
    payload.put("iss", "client");
    payload.put("aud", "https://server.example.com");
    payload.put("exp", 1_900_000_000);
    payload.put("jti", "abc");
    payload.put("member_no", "A123");

    CustomParams customParams = new RequestObjectParameters(payload).customParams();

    assertEquals(Map.of("member_no", "A123"), customParams.values());
    assertEquals(CustomParamSource.REQUEST_OBJECT, customParams.sourceOf("member_no"));
  }

  @Test
  void requestObjectTakesNumbersAndBooleansAsStringsAndLeavesOutObjectsAndArrays() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("tier", 3);
    payload.put("trial", true);
    payload.put("profile", Map.of("rank", "gold"));
    payload.put("tags", List.of("a", "b"));

    CustomParams customParams = new RequestObjectParameters(payload).customParams();

    assertEquals(Map.of("tier", "3", "trial", "true"), customParams.values());
  }

  @Test
  void queryParametersComeFromTheQuery() {
    CustomParams customParams = queryOf(Map.of("client_id", "client", "variant", "dark"));

    assertEquals(Map.of("variant", "dark"), customParams.values());
    assertEquals(CustomParamSource.QUERY, customParams.sourceOf("variant"));
  }

  @Test
  void requestObjectWinsOverTheQueryAndTheQueryFillsTheRest() {
    CustomParams query = queryOf(Map.of("member_no", "Z999", "variant", "dark"));
    CustomParams requestObject =
        new RequestObjectParameters(Map.of("member_no", "A123")).customParams();

    CustomParams assembled = query.assembledWith(requestObject);

    assertEquals(Map.of("member_no", "A123", "variant", "dark"), assembled.values());
    assertEquals(CustomParamSource.REQUEST_OBJECT, assembled.sourceOf("member_no"));
    assertEquals(CustomParamSource.QUERY, assembled.sourceOf("variant"));
  }

  @Test
  void pushedRequestCountsEveryKeyAsPushed() {
    CustomParams query = queryOf(Map.of("variant", "dark"));
    CustomParams requestObject =
        new RequestObjectParameters(Map.of("member_no", "A123")).customParams();

    CustomParams pushed = query.assembledWith(requestObject).allFrom(CustomParamSource.PUSHED);

    assertEquals(CustomParamSource.PUSHED, pushed.sourceOf("member_no"));
    assertEquals(CustomParamSource.PUSHED, pushed.sourceOf("variant"));
  }

  @Test
  void storedValuesHaveNoSource() {
    CustomParams stored = new CustomParams(Map.of("member_no", "A123"));

    assertNull(stored.sourceOf("member_no"));
  }

  private static CustomParams queryOf(Map<String, String> params) {
    Map<String, String[]> values = new HashMap<>();
    params.forEach((key, value) -> values.put(key, new String[] {value}));
    return new OAuthRequestParameters(values).customParams();
  }
}
