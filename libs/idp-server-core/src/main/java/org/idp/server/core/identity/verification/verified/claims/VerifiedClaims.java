package org.idp.server.core.identity.verification.verified.claims;

import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.application.IdentityVerificationMapper;

import java.util.Map;

public class VerifiedClaims {
    JsonNodeWrapper json;

    public VerifiedClaims() {
        this.json = JsonNodeWrapper.empty();
    }

    public VerifiedClaims(JsonNodeWrapper json) {
        this.json = json;
    }

    public static VerifiedClaims create(
            IdentityVerificationRequest request, JsonSchemaDefinition jsonSchemaDefinition) {

        Map<String, Object> mappingResult =
                IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);

        return new VerifiedClaims(JsonNodeWrapper.fromObject(mappingResult));
    }

    public String getValueOrEmptyAsString(String fieldName) {
        return json.getValueOrEmptyAsString(fieldName);
    }

    public Map<String, Object> toMap() {
        return json.toMap();
    }
}
