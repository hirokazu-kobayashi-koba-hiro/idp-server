package org.idp.server.basic.json.schema;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.format.JsonPropertyFormat;

public class JsonSchemaValidator {

  JsonSchemaDefinition schemaDefinition;

  public JsonSchemaValidator(JsonSchemaDefinition schemaDefinition) {
    this.schemaDefinition = schemaDefinition;
  }

  public JsonSchemaValidationResult validate(JsonNodeWrapper target) {
    List<String> errors = new ArrayList<>();
    if (!target.exists()) {
      errors.add("Schema does not exist");
      return JsonSchemaValidationResult.failure(errors);
    }

    validateRequiredField(target, errors);
    validateType(target, errors);

    if (!errors.isEmpty()) {
      return JsonSchemaValidationResult.failure(errors);
    }

    return JsonSchemaValidationResult.success();
  }

  void validateRequiredField(JsonNodeWrapper target, List<String> errors) {
    List<String> requiredFields = schemaDefinition.requiredFields();
    if (!requiredFields.isEmpty()) {
      for (String requiredField : requiredFields) {
        if (!target.contains(requiredField)) {
          errors.add(requiredField + " is missing");
        }
      }
    }
  }

  void validateType(JsonNodeWrapper target, List<String> errors) {
    for (String field : target.fieldNamesAsList()) {

      JsonNodeWrapper valueObject = target.getValueAsJsonNode(field);
      JsonSchemaProperty schemaProperty = schemaDefinition.propertySchema(field);
      if (!schemaProperty.exists()) {
        return;
      }
      if (!valueObject.nodeTypeAsString().equals(schemaProperty.type())) {
        errors.add(field + " type is " + schemaDefinition.propertySchema(field).type());
      }

      if (schemaProperty.isStringType()) {
        validateStringConstraints(field, valueObject, schemaProperty, errors);
      }

      if (schemaProperty.isArrayType()) {
        validateArrayConstraints(field, valueObject, schemaProperty, errors);
      }
    }
  }

  void validateStringConstraints(
      String field,
      JsonNodeWrapper valueObject,
      JsonSchemaProperty schemaProperty,
      List<String> errors) {
    String value = valueObject.asText();

    if (schemaProperty.hasMinLength() && value.length() < schemaProperty.minLength()) {
      errors.add(field + " minLength is " + schemaProperty.minLength());
    }

    if (schemaProperty.hasMaxLength() && value.length() > schemaProperty.maxLength()) {
      errors.add(field + " maxLength is " + schemaProperty.maxLength());
    }

    if (schemaProperty.hasPattern() && !value.matches(schemaProperty.pattern())) {
      errors.add(field + " pattern is " + schemaProperty.pattern());
    }

    if (schemaProperty.hasFormat()) {
      JsonPropertyFormat format = schemaProperty.format();
      if (!format.match(value)) {
        errors.add(
            String.format(
                "%s format is %s, but %s.", field, schemaProperty.formatAsString(), value));
      }
    }

    if (schemaProperty.hasEnum() && !schemaProperty.enumValues().contains(value)) {
      errors.add(
          String.format(
              "%s is not allowed enum value, input: %s, definition: %s",
              field, value, schemaProperty.enumValuesAsString()));
    }
  }

  void validateArrayConstraints(
      String field,
      JsonNodeWrapper valueObject,
      JsonSchemaProperty schemaProperty,
      List<String> errors) {

    List<JsonNodeWrapper> elements = valueObject.elements();

    if (schemaProperty.hasMinItems() && elements.size() < schemaProperty.minItems()) {
      errors.add(field + " must have at least " + schemaProperty.minItems() + " items.");
    }

    if (schemaProperty.hasMaxItems() && elements.size() > schemaProperty.maxItems()) {
      errors.add(field + " must have at most " + schemaProperty.maxItems() + " items.");
    }

    if (schemaProperty.uniqueItems()) {
      List<String> seen = new ArrayList<>();
      for (JsonNodeWrapper element : elements) {
        String asString = element.asText();
        if (seen.contains(asString)) {
          errors.add(field + " must not contain duplicate items.");
          break;
        }
        seen.add(asString);
      }
    }

    JsonSchemaProperty itemsSchema = schemaProperty.itemsSchema();
    for (JsonNodeWrapper element : elements) {
      if (itemsSchema.isStringType()) {
        validateStringConstraints(field, element, schemaProperty, errors);
      }
    }
  }
}
