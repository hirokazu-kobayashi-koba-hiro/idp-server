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

package org.idp.server.platform.http;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicAuthTest {

  private final BasicAuth configured = new BasicAuth("vendor", "secret");

  @Test
  @DisplayName("ユーザー名とパスワードが一致すれば認証できる")
  void matchesSameCredentials() {
    assertTrue(configured.matches(new BasicAuth("vendor", "secret")));
  }

  @Test
  @DisplayName("ユーザー名かパスワードのどちらかが違えば認証できない")
  void rejectsDifferentCredentials() {
    assertFalse(configured.matches(new BasicAuth("vendor", "secreT")));
    assertFalse(configured.matches(new BasicAuth("vendoR", "secret")));
    assertFalse(configured.matches(new BasicAuth("vendor", "secret-longer")));
  }

  @Test
  @DisplayName("どちらかが空なら、一致していても認証できない")
  void rejectsMissingCredentials() {
    assertFalse(configured.matches(new BasicAuth()));
    assertFalse(configured.matches(null));
    assertFalse(new BasicAuth().matches(new BasicAuth()));
    assertFalse(new BasicAuth().matches(new BasicAuth("vendor", "secret")));
    assertFalse(new BasicAuth("", "").matches(new BasicAuth("", "")));
  }
}
