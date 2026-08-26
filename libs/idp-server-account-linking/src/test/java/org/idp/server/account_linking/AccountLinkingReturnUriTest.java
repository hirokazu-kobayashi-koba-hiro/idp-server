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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountLinkingReturnUriTest {

  static final AccountLinkingState STATE = new AccountLinkingState("abc-123");

  @Test
  @DisplayName("クエリが無ければ ? で繋ぐ")
  void appendsWithQuestionMark() {
    String value =
        new AccountLinkingReturnUri("https://rp.example.com/linking/callback", STATE).value();

    assertTrue(value.startsWith("https://rp.example.com/linking/callback?"));
    assertTrue(value.contains("linking=done"));
    assertTrue(value.contains("state=abc-123"));
  }

  @Test
  @DisplayName("既にクエリがあれば & で繋ぐ")
  void appendsWithAmpersand() {
    String value =
        new AccountLinkingReturnUri("https://rp.example.com/linking/callback?from=settings", STATE)
            .value();

    assertTrue(value.startsWith("https://rp.example.com/linking/callback?from=settings&"));
    assertFalse(value.replaceFirst("\\?", "").contains("?"));
  }
}
