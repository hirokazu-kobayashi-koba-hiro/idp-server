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

package org.idp.server.identity.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.address.Address;
import org.idp.server.core.oidc.identity.mapper.UserInfoMapper;
import org.idp.server.core.oidc.identity.mapper.UserinfoMappingRule;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UserInfoMapperTest {

  @Test
  public void testBasicUserMapping() throws Exception {
    String json =
        "{\"email\": \"test@example.com\", \"name\": \"Test User\", \"email_verified\": true}";
    JsonNodeWrapper body = JsonNodeWrapper.fromString(json);

    List<UserinfoMappingRule> rules =
        List.of(
            new UserinfoMappingRule("body", "email", "email", "string"),
            new UserinfoMappingRule("body", "name", "name", "string"),
            new UserinfoMappingRule("body", "email_verified", "email_verified", "boolean"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", Map.of(), body, rules);
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

    List<UserinfoMappingRule> rules =
        List.of(new UserinfoMappingRule("body", "address", "address", "address"));

    UserInfoMapper userInfoMapper = new UserInfoMapper("test", Map.of(), body, rules);
    User user = userInfoMapper.toUser();
    Address address = user.address();

    assertNotNull(address);
    assertEquals("123 Main St", address.formatted());
    assertEquals("Springfield", address.locality());
    assertEquals("IL", address.region());
    assertEquals("62704", address.postalCode());
    assertEquals("USA", address.country());
  }

  static Stream<Arguments> mappingCases() {
    return Stream.of(
        Arguments.of("string", "{\"name\": \"John\"}", "body", "name", "name", "John"),
        Arguments.of("int", "{\"age\": 25}", "body", "age", "custom_age", 25),
        Arguments.of(
            "boolean",
            "{\"email_verified\": true}",
            "body",
            "email_verified",
            "email_verified",
            true),
        Arguments.of(
            "list<string>",
            "{\"roles\": [\"admin\", \"user\"]}",
            "body",
            "roles",
            "permissions",
            List.of("admin", "user")),
        Arguments.of(
            "object",
            "{\"profile\": {\"job\": \"engineer\"}}",
            "body",
            "profile",
            "custom_profile",
            Map.of("job", "engineer")),
        Arguments.of(
            "address",
            """
            {
              "address": {
                "formatted": "123 Main St",
                "street_address": "123 Main St",
                "locality": "Springfield",
                "region": "IL",
                "postal_code": "62704",
                "country": "USA"
              }
            }""",
            "body",
            "address",
            "address",
            null));
  }

  @ParameterizedTest
  @MethodSource("mappingCases")
  void testMappingTypes(
      String type, String json, String source, String from, String to, Object expected) {
    JsonNodeWrapper body = JsonNodeWrapper.fromString(json);
    UserinfoMappingRule rule = new UserinfoMappingRule(source, from, to, type);
    List<UserinfoMappingRule> rules = List.of(rule);
    UserInfoMapper mapper = new UserInfoMapper("test", Map.of(), body, rules);
    User user = mapper.toUser();

    switch (to) {
      case "name" -> assertEquals(expected, user.name());
      case "email_verified" -> assertEquals(expected, user.emailVerified());
      case "permissions" -> assertEquals(expected, user.permissions());
      case "custom_age", "custom_profile", "custom_value" ->
          assertEquals(expected, user.customPropertiesValue().get(to));
      case "address" -> {
        Address addr = user.address();
        assertNotNull(addr);
        assertEquals("123 Main St", addr.formatted());
        assertEquals("Springfield", addr.locality());
        assertEquals("IL", addr.region());
        assertEquals("62704", addr.postalCode());
        assertEquals("USA", addr.country());
      }
      default -> fail("Unknown target property: " + to);
    }
  }

  static Stream<Arguments> listObjectWithFieldCases() {
    return Stream.of(
        Arguments.of(
            "{\"items\": [{\"value\": \"a\"}, {\"value\": \"b\"}]}",
            "items",
            "custom_value",
            "list<object>",
            "b",
            1,
            "value"));
  }

  @ParameterizedTest
  @MethodSource("listObjectWithFieldCases")
  void testListObjectField(
      String json,
      String from,
      String to,
      String type,
      Object expected,
      int itemIndex,
      String field) {
    JsonNodeWrapper body = JsonNodeWrapper.fromString(json);
    UserinfoMappingRule rule = new UserinfoMappingRule("body", from, to, type, itemIndex, field);
    UserInfoMapper mapper = new UserInfoMapper("test", Map.of(), body, List.of(rule));
    User user = mapper.toUser();

    assertEquals(expected, user.customPropertiesValue().get(to));
  }

  static Stream<Arguments> headerMappingCases() {
    return Stream.of(
        Arguments.of(
            "string",
            Map.of("x-name", List.of("Header User")),
            "header",
            "x-name",
            "name",
            "Header User"),
        Arguments.of("int", Map.of("x-age", List.of("42")), "header", "x-age", "custom_age", 42),
        Arguments.of(
            "boolean",
            Map.of("x-active", List.of("true")),
            "header",
            "x-active",
            "email_verified",
            true),
        Arguments.of(
            "string",
            Map.of("x-role", List.of("admin")),
            "header",
            "x-role",
            "custom_role",
            "admin"));
  }

  @ParameterizedTest
  @MethodSource("headerMappingCases")
  void testHeaderMapping(
      String type,
      Map<String, List<String>> headerMap,
      String source,
      String from,
      String to,
      Object expected) {
    JsonNodeWrapper body = JsonNodeWrapper.fromString("{}");
    UserinfoMappingRule rule = new UserinfoMappingRule(source, from, to, type);
    UserInfoMapper mapper = new UserInfoMapper("test", headerMap, body, List.of(rule));
    User user = mapper.toUser();

    switch (to) {
      case "name" -> assertEquals(expected, user.name());
      case "email_verified" -> assertEquals(expected, user.emailVerified());
      default -> assertEquals(expected, user.customPropertiesValue().get(to));
    }
  }
}
