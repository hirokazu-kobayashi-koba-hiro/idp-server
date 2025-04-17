package org.idp.server.core.basic.json.schema;

import org.idp.server.core.basic.json.JsonNodeWrapper;

import java.util.ArrayList;
import java.util.List;

public class JsonSchemaValidator {

    JsonSchemaDefinition schemaDefinition;

    public JsonSchemaValidator(JsonSchemaDefinition schemaDefinition) {
        this.schemaDefinition = schemaDefinition;
    }

    public JsonSchemaValidationResult validate(JsonNodeWrapper target) {
        List<String> errors = new ArrayList<>();
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
        target.fieldNames().forEachRemaining(field -> {
            JsonNodeWrapper valueObject = target.getValueAsJsonNode(field);
            JsonSchemaProperty schemaProperty = schemaDefinition.propertySchema(field);
            if (!schemaProperty.exists()) {
                return;
            }
            if (!valueObject.nodeTypeAsString().equals(schemaProperty.type())) {
                errors.add(field + " type is " + schemaDefinition.propertySchema(field).type());
            }

            if (schemaProperty.hasMinLength()) {
                if (valueObject.isString() && valueObject.asText().length() < schemaProperty.minLength()) {
                    errors.add(field + " minLength is " + schemaProperty.minLength());
                }
            }

            if (schemaProperty.hasMaxLength()) {
                if (valueObject.isString() && valueObject.asText().length() > schemaProperty.maxLength()) {
                    errors.add(field + " maxLength is " + schemaProperty.maxLength());
                }
            }

            if (schemaProperty.hasPattern()) {
                if (valueObject.isString() && !valueObject.asText().matches(schemaProperty.pattern())) {
                    errors.add(field + " pattern is " + schemaProperty.pattern());
                }
            }
        });
    }


}
