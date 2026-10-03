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

package org.idp.server.platform.multi_tenancy.tenant.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsConfigurationEffectiveAllowHeadersTest {

  @Test
  @DisplayName("保存済みの allow_headers に x-view-binding が無くても、応答では許可する")
  void addsViewBindingToStoredList() {
    CorsConfiguration configuration =
        new CorsConfiguration(
            Map.of("allow_headers", "Authorization, Content-Type, Accept, x-device-id"));

    assertEquals(
        "Authorization, Content-Type, Accept, x-device-id, x-view-binding",
        configuration.effectiveAllowHeaders());
  }

  @Test
  @DisplayName("既に含まれていれば重ねない（大文字小文字は区別しない）")
  void doesNotDuplicate() {
    CorsConfiguration configuration =
        new CorsConfiguration(Map.of("allow_headers", "Content-Type, X-View-Binding"));

    assertEquals("Content-Type, X-View-Binding", configuration.effectiveAllowHeaders());
  }

  @Test
  @DisplayName("既定値にも足す")
  void addsToDefault() {
    assertTrue(new CorsConfiguration().effectiveAllowHeaders().endsWith(", x-view-binding"));
  }
}
