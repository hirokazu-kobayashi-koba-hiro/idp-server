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

package org.idp.server.core.openid.grant_management.grant.consent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * Verifies the serialization difference between toMap() and toSerializableMap().
 *
 * <p>toMap() returns {@code Map<String, List<ConsentClaim>>} which relies on the ObjectMapper's
 * field visibility settings. JsonConverter (FIELD=ANY) can serialize it, but Spring Boot's default
 * ObjectMapper cannot because ConsentClaim has package-private fields with non-JavaBean getters.
 *
 * <p>toSerializableMap() returns {@code Map<String, List<Map<String, Object>>>} which any
 * ObjectMapper can serialize correctly regardless of visibility settings.
 *
 * @see <a href="https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1351">#1351</a>
 */
class ConsentClaimsSerializationTest {

  @Test
  void toMapSerializesWithJsonConverter() {
    ConsentClaims consentClaims = createTestConsentClaims();

    // JsonConverter uses FIELD visibility (same as DB write path)
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    String json = jsonConverter.write(consentClaims.toMap());

    assertTrue(json.contains("tos_uri"), "should contain name: " + json);
    assertTrue(json.contains("https://example.com/terms"), "should contain value: " + json);
    assertTrue(json.contains("consented_at"), "should contain consented_at: " + json);
  }

  @Test
  void toSerializableMapContainsAllFields() {
    ConsentClaims consentClaims = createTestConsentClaims();

    Map<String, List<Map<String, Object>>> result = consentClaims.toSerializableMap();

    // Verify terms
    List<Map<String, Object>> terms = result.get("terms");
    assertNotNull(terms);
    assertEquals(1, terms.size());
    assertEquals("tos_uri", terms.get(0).get("name"));
    assertEquals("https://example.com/terms", terms.get(0).get("value"));
    assertNotNull(terms.get(0).get("consented_at"));

    // Verify privacy
    List<Map<String, Object>> privacy = result.get("privacy");
    assertNotNull(privacy);
    assertEquals(1, privacy.size());
    assertEquals("policy_uri", privacy.get(0).get("name"));
    assertEquals("https://example.com/privacy", privacy.get(0).get("value"));
    assertNotNull(privacy.get(0).get("consented_at"));
  }

  @Test
  void toSerializableMapSerializesWithJsonConverter() {
    ConsentClaims consentClaims = createTestConsentClaims();

    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    String json = jsonConverter.write(consentClaims.toSerializableMap());

    assertTrue(json.contains("tos_uri"), "should contain name: " + json);
    assertTrue(json.contains("https://example.com/terms"), "should contain value: " + json);
    assertTrue(json.contains("consented_at"), "should contain consented_at: " + json);
    assertFalse(json.contains("{}"), "should NOT contain empty objects: " + json);
  }

  @Test
  void toMapAndToSerializableMapDifferInDateTimeFormat() {
    ConsentClaims consentClaims = createTestConsentClaims();

    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    String fromToMap = jsonConverter.write(consentClaims.toMap());
    String fromToSerializableMap = jsonConverter.write(consentClaims.toSerializableMap());

    // toMap(): LocalDateTime is serialized as array [2026,4,1,12,0] by Jackson
    assertTrue(fromToMap.contains("[2026,4,1,12,0]"));

    // toSerializableMap(): LocalDateTime is converted to ISO string via toString()
    assertTrue(fromToSerializableMap.contains("2026-04-01T12:00"));

    // Both contain the same claim data
    assertTrue(fromToMap.contains("tos_uri"));
    assertTrue(fromToSerializableMap.contains("tos_uri"));
  }

  private ConsentClaims createTestConsentClaims() {
    ConsentClaim termsConsent =
        new ConsentClaim(
            "tos_uri", "https://example.com/terms", LocalDateTime.of(2026, 4, 1, 12, 0));
    ConsentClaim privacyConsent =
        new ConsentClaim(
            "policy_uri", "https://example.com/privacy", LocalDateTime.of(2026, 4, 1, 12, 0));
    return new ConsentClaims(
        Map.of("terms", List.of(termsConsent), "privacy", List.of(privacyConsent)));
  }
}
