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


package org.idp.server.authentication.interactors.legacy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.address.Address;
import org.junit.jupiter.api.Test;

public class UserInfoMapperTest {

  @Test
  public void testBasicUserMapping() throws Exception {
    String json =
        "{\"email\": \"test@example.com\", \"name\": \"Test User\", \"email_verified\": true}";
    JsonNodeWrapper body = JsonNodeWrapper.fromString(json);

    List<UserInfoMappingRule> rules =
        List.of(
            new UserInfoMappingRule("email", "email", "string"),
            new UserInfoMappingRule("name", "name", "string"),
            new UserInfoMappingRule("email_verified", "email_verified", "boolean"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", body, rules);
    User user = userInfoMapper.toUser();

    assertEquals("test@example.com", user.email());
    assertEquals("Test User", user.name());
    assertTrue(user.emailVerified());
  }

  @Test
  public void testAddressMapping() throws Exception {
    String json =
        "{\"address\": {\"formatted\": \"123 Main St\", \"street_address\": \"123 Main St\", \"locality\": \"Springfield\", \"region\": \"IL\", \"postal_code\": \"62704\", \"country\": \"USA\"}}";
    JsonNodeWrapper body = JsonNodeWrapper.fromString(json);

    List<UserInfoMappingRule> rules =
        List.of(new UserInfoMappingRule("address", "address", "address"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", body, rules);
    User user = userInfoMapper.toUser();
    Address address = user.address();

    assertNotNull(address);
    assertEquals("123 Main St", address.formatted());
    assertEquals("Springfield", address.locality());
    assertEquals("IL", address.region());
    assertEquals("62704", address.postalCode());
    assertEquals("USA", address.country());
  }
}
