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

import org.idp.server.core.openid.identity.User;
import org.junit.jupiter.api.Test;

class ExternalAccountIdentityTest {

  @Test
  void takesTheAccountFromTheVerifiedSubjectOverTheUserinfo() {
    User federatedUser = new User().setExternalUserId("from-userinfo");

    assertEquals(
        "verified-sub",
        ExternalAccountIdentity.of("verified-sub", federatedUser).federatedUserId());
  }

  @Test
  void takesTheAccountFromTheUserinfoWithoutAnIdToken() {
    User federatedUser = new User().setExternalUserId("from-userinfo");

    assertEquals(
        "from-userinfo", ExternalAccountIdentity.of(null, federatedUser).federatedUserId());
  }

  @Test
  void namesTheAccountByPreferredUsernameOverEmail() {
    User federatedUser = new User().setPreferredUsername("octocat").setEmail("octocat@example.com");

    assertEquals("octocat", ExternalAccountIdentity.of(null, federatedUser).federatedUsername());
  }

  @Test
  void namesTheAccountByEmailWithoutAPreferredUsername() {
    assertEquals(
        "octocat@example.com",
        ExternalAccountIdentity.of(null, new User().setEmail("octocat@example.com"))
            .federatedUsername());
    assertEquals(
        "octocat@example.com",
        ExternalAccountIdentity.of(
                null, new User().setPreferredUsername(" ").setEmail("octocat@example.com"))
            .federatedUsername());
  }

  @Test
  void namesNothingWithoutAUserinfo() {
    ExternalAccountIdentity identity = ExternalAccountIdentity.of("verified-sub", null);

    assertEquals("verified-sub", identity.federatedUserId());
    assertNull(identity.federatedUsername());
  }

  @Test
  void identifiesNothingForAPlainDelegation() {
    ExternalAccountIdentity identity = ExternalAccountIdentity.of(null, null);

    assertNull(identity.federatedUserId());
    assertNull(identity.federatedUsername());
  }
}
