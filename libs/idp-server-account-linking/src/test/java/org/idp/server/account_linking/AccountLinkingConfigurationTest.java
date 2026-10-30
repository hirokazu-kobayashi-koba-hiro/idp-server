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

package org.idp.server.account_linking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class AccountLinkingConfigurationTest {

  static AccountLinkingConfiguration read(Map<String, Object> payload) {
    return JsonConverter.snakeCaseInstance().read(payload, AccountLinkingConfiguration.class);
  }

  @Test
  void refreshesThirtySecondsBeforeExpiryWhenNotSet() {
    assertEquals(30, read(Map.of()).refreshBeforeExpirySeconds());
  }

  @Test
  void readsRefreshBeforeExpirySeconds() {
    assertEquals(
        300, read(Map.of("refresh_before_expiry_seconds", 300)).refreshBeforeExpirySeconds());
    assertEquals(0, read(Map.of("refresh_before_expiry_seconds", 0)).refreshBeforeExpirySeconds());
  }
}
