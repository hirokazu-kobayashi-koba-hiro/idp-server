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

package org.idp.server.core.openid.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StandardClaimsTest {

  @Test
  void resolvesPresentStandardClaimsFromUserFields() {
    User user =
        new User().setName("Alice Example").setEmail("alice@example.com").setEmailVerified(true);

    assertEquals(Optional.of("Alice Example"), StandardClaims.resolve(user, "name"));
    assertEquals(Optional.of("alice@example.com"), StandardClaims.resolve(user, "email"));
    assertEquals(Optional.of(true), StandardClaims.resolve(user, "email_verified"));
  }

  @Test
  void returnsEmptyForAbsentStandardClaims() {
    User user = new User().setEmail("alice@example.com");

    // set but not requested-here fields resolve, unset ones are empty (never null)
    assertTrue(StandardClaims.resolve(user, "given_name").isEmpty());
    assertTrue(StandardClaims.resolve(user, "phone_number").isEmpty());
    assertTrue(StandardClaims.resolve(user, "address").isEmpty());
    assertTrue(StandardClaims.resolve(user, "email_verified").isEmpty());
  }

  @Test
  void returnsEmptyForUnknownOrNonStandardClaimNames() {
    User user = new User().setName("Alice").setEmail("alice@example.com");

    // "sub" is always present on the token itself and is intentionally not a standard_claims target
    assertTrue(StandardClaims.resolve(user, "sub").isEmpty());
    // arbitrary / custom_property-style names are not resolved by the standard resolver
    assertTrue(StandardClaims.resolve(user, "roles").isEmpty());
    assertTrue(StandardClaims.resolve(user, "not_a_claim").isEmpty());
    assertTrue(StandardClaims.resolve(user, "").isEmpty());
  }
}
