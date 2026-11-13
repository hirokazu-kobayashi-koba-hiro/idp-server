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
package org.idp.server.account_linking.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountLinkingStartRequestTest {

  static AccountLinkingStartRequest withScope(Object scope) {
    return new AccountLinkingStartRequest(null, null, null, null, Map.of("scope", scope));
  }

  /** A blank scope stored as given would reach the provider without the openid the checks need. */
  @Test
  void treatsABlankScopeAsAbsent() {
    assertNull(withScope("").scope());
    assertNull(withScope("   ").scope());
    assertNull(new AccountLinkingStartRequest(null, null, null, null, Map.of()).scope());
  }

  @Test
  void trimsAGivenScope() {
    assertEquals("openid profile", withScope("  openid profile ").scope());
  }
}
