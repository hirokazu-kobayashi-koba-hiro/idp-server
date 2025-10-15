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

package org.idp.server.platform.security.log;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityEventLogConfigurationTest {

  @Test
  void getDetailScrubKeys_shouldReturnEssentialKeysWhenNotConfigured() {
    // Given
    SecurityEventLogConfiguration config = new SecurityEventLogConfiguration(Map.of());

    // When
    List<String> result = config.getDetailScrubKeys();

    // Then
    assertEquals(9, result.size());
    assertTrue(result.contains("authorization"));
    assertTrue(result.contains("cookie"));
    assertTrue(result.contains("password"));
    assertTrue(result.contains("secret"));
    assertTrue(result.contains("token"));
    assertTrue(config.hasDetailScrubKeys());
  }

  @Test
  void getDetailScrubKeys_shouldReturnEssentialKeysWhenEmptyString() {
    // Given
    Map<String, Object> attributes = Map.of("detail_scrub_keys", "");
    SecurityEventLogConfiguration config = new SecurityEventLogConfiguration(attributes);

    // When
    List<String> result = config.getDetailScrubKeys();

    // Then
    assertEquals(9, result.size());
    assertTrue(result.contains("authorization"));
    assertTrue(result.contains("cookie"));
    assertTrue(result.contains("password"));
    assertTrue(result.contains("secret"));
    assertTrue(result.contains("token"));
    assertTrue(config.hasDetailScrubKeys());
  }

  @Test
  void getDetailScrubKeys_shouldMergeConfiguredKeysWithEssentialKeys() {
    // Given
    Map<String, Object> attributes =
        Map.of("detail_scrub_keys", "authorization,cookie,password,secret");
    SecurityEventLogConfiguration config = new SecurityEventLogConfiguration(attributes);

    // When
    List<String> result = config.getDetailScrubKeys();

    // Then
    assertEquals(9, result.size()); // 4 configured + 1 additional essential (token)
    assertTrue(result.contains("authorization"));
    assertTrue(result.contains("cookie"));
    assertTrue(result.contains("password"));
    assertTrue(result.contains("secret"));
    assertTrue(result.contains("token")); // Essential key added automatically
    assertTrue(config.hasDetailScrubKeys());
  }

  @Test
  void getDetailScrubKeys_shouldHandleSingleKey() {
    // Given
    Map<String, Object> attributes = Map.of("detail_scrub_keys", "custom_key");
    SecurityEventLogConfiguration config = new SecurityEventLogConfiguration(attributes);

    // When
    List<String> result = config.getDetailScrubKeys();

    // Then
    assertEquals(10, result.size()); // 1 configured + 5 essential keys
    assertTrue(result.contains("custom_key"));
    assertTrue(result.contains("authorization"));
    assertTrue(result.contains("cookie"));
    assertTrue(result.contains("password"));
    assertTrue(result.contains("secret"));
    assertTrue(result.contains("token"));
    assertTrue(config.hasDetailScrubKeys());
  }

  @Test
  void getDetailScrubKeys_shouldHandleRecommendedConfiguration() {
    // Given
    Map<String, Object> attributes =
        Map.of(
            "detail_scrub_keys",
            "authorization,cookie,set-cookie,proxy-authorization,password,secret,token,refresh_token,access_token,id_token,client_secret,api_key,bearer");
    SecurityEventLogConfiguration config = new SecurityEventLogConfiguration(attributes);

    // When
    List<String> result = config.getDetailScrubKeys();

    // Then
    assertEquals(14, result.size());
    assertTrue(result.contains("authorization"));
    assertTrue(result.contains("cookie"));
    assertTrue(result.contains("set-cookie"));
    assertTrue(result.contains("proxy-authorization"));
    assertTrue(result.contains("password"));
    assertTrue(result.contains("secret"));
    assertTrue(result.contains("token"));
    assertTrue(result.contains("refresh_token"));
    assertTrue(result.contains("access_token"));
    assertTrue(result.contains("id_token"));
    assertTrue(result.contains("client_secret"));
    assertTrue(result.contains("api_key"));
    assertTrue(result.contains("bearer"));
    assertTrue(config.hasDetailScrubKeys());
  }
}
