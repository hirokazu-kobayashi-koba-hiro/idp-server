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

package org.idp.server.core.openid.oauth.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestedClaimsViewDataTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> entry(Map<String, Object> result, String claim) {
    return (Map<String, Object>) result.get(claim);
  }

  @Test
  void exposesEscapedPurposeAndEssential() {
    Map<String, Object> result =
        RequestedClaimsViewData.from(
            "{\"id_token\":{\"given_name\":{\"essential\":true,\"purpose\":\"<script>alert(1)</script>\"}}}");

    assertTrue(result.containsKey("given_name"));
    assertEquals(true, entry(result, "given_name").get("essential"));
    assertEquals(
        "&lt;script&gt;alert(1)&lt;/script&gt;", entry(result, "given_name").get("purpose"));
  }

  @Test
  void escapesAllHtmlMetacharacters() {
    Map<String, Object> result =
        RequestedClaimsViewData.from("{\"userinfo\":{\"email\":{\"purpose\":\"a&b<c>\\\"d'e\"}}}");

    assertEquals("a&amp;b&lt;c&gt;&quot;d&#39;e", entry(result, "email").get("purpose"));
  }

  @Test
  void mergesIdTokenAndUserinfoAndDefaultsEssentialToFalse() {
    Map<String, Object> result =
        RequestedClaimsViewData.from(
            "{\"id_token\":{\"given_name\":{\"purpose\":\"abc\"}},"
                + "\"userinfo\":{\"email\":{\"purpose\":\"def\"}}}");

    assertEquals(2, result.size());
    assertEquals(false, entry(result, "given_name").get("essential"));
    assertEquals("abc", entry(result, "given_name").get("purpose"));
    assertEquals("def", entry(result, "email").get("purpose"));
  }

  @Test
  void omitsClaimsWithoutPurposeAndTheVerifiedClaimsMember() {
    Map<String, Object> result =
        RequestedClaimsViewData.from(
            "{\"id_token\":{\"given_name\":null,\"email\":{\"essential\":true},"
                + "\"verified_claims\":{\"claims\":{\"birthdate\":{\"purpose\":\"ignored\"}}}}}");

    assertTrue(result.isEmpty());
  }

  @Test
  void returnsEmptyForBlankClaims() {
    assertTrue(RequestedClaimsViewData.from(null).isEmpty());
    assertTrue(RequestedClaimsViewData.from("").isEmpty());
  }
}
