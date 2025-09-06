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

package org.idp.server.notification.push.apns;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.notification.NotificationTemplate;
import org.junit.jupiter.api.Test;

class ApnsConfigurationTest {

  @Test
  void testConstructorAndGetters() {
    String keyId = "ABC123DEF4";
    String teamId = "DEF123GHIJ";
    String bundleId = "com.example.app";
    String keyContent =
        "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC...\n-----END PRIVATE KEY-----";
    boolean production = true;
    Map<String, NotificationTemplate> templates = new HashMap<>();
    templates.put("default", new NotificationTemplate());

    ApnsConfiguration config =
        new ApnsConfiguration(keyId, teamId, bundleId, keyContent, production, templates);

    assertEquals(keyId, config.keyId());
    assertEquals(teamId, config.teamId());
    assertEquals(bundleId, config.bundleId());
    assertEquals(keyContent, config.keyContent());
    assertTrue(config.isProduction());
    assertNotNull(config.findTemplate("default"));
  }

  @Test
  void testFindTemplateReturnsDefaultWhenNotFound() {
    ApnsConfiguration config =
        new ApnsConfiguration("key", "team", "bundle", "content", false, new HashMap<>());

    NotificationTemplate template = config.findTemplate("nonexistent");

    assertNotNull(template);
  }
}
