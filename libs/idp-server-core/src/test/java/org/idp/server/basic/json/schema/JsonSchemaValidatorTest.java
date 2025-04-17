package org.idp.server.basic.json.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonSchemaValidatorTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testValidationFailsWhenRequiredFieldIsMissing() throws Exception {
        String schemaJson = """
      {
        "type": "object",
        "required": ["email"],
        "properties": {
          "email": { "type": "string" }
        }
      }
      """;
        JsonSchemaDefinition schemaDefinition = new JsonSchemaDefinition(
                new JsonNodeWrapper(objectMapper.readTree(schemaJson)));

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
        String schemaJson = """
      {
        "type": "object",
        "required": ["email"],
        "properties": {
          "email": { "type": "string" }
        }
      }
      """;
        JsonSchemaDefinition schemaDefinition = new JsonSchemaDefinition(
                new JsonNodeWrapper(objectMapper.readTree(schemaJson)));

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
        String schemaJson = """
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
        String schemaJson = """
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
        String schemaJson = """
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

        assertFalse(result.isValid());
        assertTrue(result.errors().contains("email_verified type is boolean"));
    }

    @Test
    public void testValidStringWithinLimits() throws Exception {
        String schemaJson = """
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
        String schemaJson = """
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
        assertTrue(result.errors().contains("password pattern is ^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()]).+$"));
    }

    @Test
    public void testPasswordPassesPattern() throws Exception {
        String schemaJson = """
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
}