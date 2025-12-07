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

package org.idp.server.platform.json.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

public class JsonSchemaValidatorTest {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testValidationFailsWhenRequiredFieldIsMissing() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "required": ["email"],
        "properties": {
          "email": { "type": "string" }
        }
      }
      """;
    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));

    String json = """
      {
        "name": "taro"
      }
      """;
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));

    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertFalse(result.isValid());
    assertTrue(result.errors().contains("email is missing"));
  }

  @Test
  public void testValidationSucceedsWhenAllRequiredFieldsExist() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "required": ["email"],
        "properties": {
          "email": { "type": "string" }
        }
      }
      """;
    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));

    String json = """
      {
        "email": "user@example.com"
      }
      """;
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));

    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertTrue(result.isValid());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  public void testStringTooShort() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "username": {
            "type": "string",
            "minLength": 5
          }
        }
      }
      """;

    String json = """
      {
        "username": "abc"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertFalse(result.isValid());
    assertTrue(result.errors().contains("username minLength is 5"));
  }

  @Test
  public void testStringTooLong() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "nickname": {
            "type": "string",
            "maxLength": 5
          }
        }
      }
      """;

    String json = """
      {
        "nickname": "abcdef"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertFalse(result.isValid());
    assertTrue(result.errors().contains("nickname maxLength is 5"));
  }

  @Test
  public void testWrongType() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "email_verified": {
            "type": "boolean"
          }
        }
      }
      """;

    String json = """
      {
        "email_verified": "true"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    //    assertFalse(result.isValid());
    //    assertTrue(result.errors().contains("email_verified type is boolean"));
  }

  @Test
  public void testValidStringWithinLimits() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "username": {
            "type": "string",
            "minLength": 3,
            "maxLength": 10
          }
        }
      }
      """;

    String json = """
      {
        "username": "foobar"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertTrue(result.isValid());
  }

  @Test
  public void testPasswordFailsPattern() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "password": {
            "type": "string",
            "minLength": 8,
            "pattern": "^(?=.*[A-Z])(?=.*\\\\d)(?=.*[!@#$%^&*()]).+$"
          }
        }
      }
      """;

    String json = """
      {
        "password": "simplepass"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertFalse(result.isValid());
    assertTrue(
        result.errors().contains("password pattern is ^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()]).+$"));
  }

  @Test
  public void testPasswordPassesPattern() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "password": {
            "type": "string",
            "minLength": 8,
            "pattern": "^(?=.*[A-Z])(?=.*\\\\d)(?=.*[!@#$%^&*()]).+$"
          }
        }
      }
      """;

    String json = """
      {
        "password": "Password123!"
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonNodeWrapper input = new JsonNodeWrapper(objectMapper.readTree(json));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertTrue(result.isValid());
  }

  @Test
  public void testEnumVariousTypes() {
    String schemaJson =
        """
            {
               "type": "object",
               "properties": {
                 "notification_channel": {
                   "type": "string",
                   "enum": [
                     "fcm", ""
                   ]
                 }
               }
             }
            """;

    String json = """
      {
        "notification_channel": "Password123!"
      }
      """;

    JsonSchemaDefinition schemaDefinition = JsonSchemaDefinition.fromJson(schemaJson);
    JsonNodeWrapper input = JsonNodeWrapper.fromString(json);
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
    JsonSchemaValidationResult result = validator.validate(input);

    assertFalse(result.isValid());

    String nullJson = """
      {
        "notification_channel": ""
      }
    """;
    JsonNodeWrapper emptyInput = JsonNodeWrapper.fromString(nullJson);
    JsonSchemaValidationResult nullResult = validator.validate(emptyInput);

    assertTrue(nullResult.isValid());
  }

  @Test
  public void testIpAddressValidPattern() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "ip_address": {
            "type": "string",
            "pattern": "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^([0-9a-fA-F]{1,4}:){1,7}:$|^::[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){0,5}$"
          }
        }
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);

    // Valid IPv4 addresses
    String validIp1 = """
      { "ip_address": "127.0.0.1" }
      """;
    JsonSchemaValidationResult result1 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIp1)));
    assertTrue(result1.isValid(), "127.0.0.1 should be valid");

    String validIp2 = """
      { "ip_address": "192.168.1.100" }
      """;
    JsonSchemaValidationResult result2 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIp2)));
    assertTrue(result2.isValid(), "192.168.1.100 should be valid");

    String validIp3 = """
      { "ip_address": "255.255.255.255" }
      """;
    JsonSchemaValidationResult result3 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIp3)));
    assertTrue(result3.isValid(), "255.255.255.255 should be valid");

    String validIp4 = """
      { "ip_address": "0.0.0.0" }
      """;
    JsonSchemaValidationResult result4 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIp4)));
    assertTrue(result4.isValid(), "0.0.0.0 should be valid");
  }

  @Test
  public void testIpAddressInvalidPattern() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "ip_address": {
            "type": "string",
            "pattern": "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^([0-9a-fA-F]{1,4}:){1,7}:$|^::[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){0,5}$"
          }
        }
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);

    // Invalid: non-IP string
    String invalidIp1 = """
      { "ip_address": "invalid-ip" }
      """;
    JsonSchemaValidationResult result1 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(invalidIp1)));
    assertFalse(result1.isValid(), "invalid-ip should be invalid");

    // Invalid: out of range (999 > 255)
    String invalidIp2 = """
      { "ip_address": "999.999.999.999" }
      """;
    JsonSchemaValidationResult result2 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(invalidIp2)));
    assertFalse(result2.isValid(), "999.999.999.999 should be invalid (out of range)");

    // Invalid: 256 > 255
    String invalidIp3 = """
      { "ip_address": "256.1.1.1" }
      """;
    JsonSchemaValidationResult result3 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(invalidIp3)));
    assertFalse(result3.isValid(), "256.1.1.1 should be invalid (256 > 255)");

    // Invalid: missing octet
    String invalidIp4 = """
      { "ip_address": "192.168.1" }
      """;
    JsonSchemaValidationResult result4 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(invalidIp4)));
    assertFalse(result4.isValid(), "192.168.1 should be invalid (missing octet)");

    // Invalid: extra octet
    String invalidIp5 = """
      { "ip_address": "192.168.1.1.1" }
      """;
    JsonSchemaValidationResult result5 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(invalidIp5)));
    assertFalse(result5.isValid(), "192.168.1.1.1 should be invalid (extra octet)");
  }

  @Test
  public void testIpAddressValidIPv6Pattern() throws Exception {
    String schemaJson =
        """
      {
        "type": "object",
        "properties": {
          "ip_address": {
            "type": "string",
            "pattern": "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^([0-9a-fA-F]{1,4}:){1,7}:$|^::[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){0,5}$"
          }
        }
      }
      """;

    JsonSchemaDefinition schemaDefinition =
        new JsonSchemaDefinition(new JsonNodeWrapper(objectMapper.readTree(schemaJson)));
    JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);

    // Valid IPv6: full form
    String validIpv6_1 =
        """
      { "ip_address": "2001:0db8:85a3:0000:0000:8a2e:0370:7334" }
      """;
    JsonSchemaValidationResult result1 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIpv6_1)));
    assertTrue(result1.isValid(), "Full IPv6 address should be valid");

    // Valid IPv6: loopback shorthand
    String validIpv6_2 = """
      { "ip_address": "::" }
      """;
    JsonSchemaValidationResult result2 =
        validator.validate(new JsonNodeWrapper(objectMapper.readTree(validIpv6_2)));
    assertTrue(result2.isValid(), ":: (IPv6 unspecified) should be valid");
  }
}
