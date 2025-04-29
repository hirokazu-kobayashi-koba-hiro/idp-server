package org.idp.server.core.identity.verification.configuration;

import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.legacy.UserInfoMappingRule;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  String url;
  String method;
  Map<String, String> headers;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;
  List<UserInfoMappingRule> userinfoMappingRules;
  Map<String, Object> requestValidationSchema;
  Map<String, Object> requestVerificationSchema;
  Map<String, Object> responseValidationSchema;
  Map<String, Object> rejectedConditionSchema;

  public IdentityVerificationProcessConfiguration() {}

  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod httpMethod() {
    return HttpMethod.valueOf(method);
  }

  public HttpRequestHeaders httpRequestHeaders() {
    return new HttpRequestHeaders(headers);
  }

  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  public HttpRequestStaticBody httpRequestStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }

  public List<UserInfoMappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }

  public Map<String, Object> requestValidationSchema() {
    return requestValidationSchema;
  }

  public Map<String, Object> requestVerificationSchema() {
    return requestVerificationSchema;
  }

  public Map<String, Object> responseValidationSchema() {
    return responseValidationSchema;
  }

  public Map<String, Object> rejectedConditionSchema() {
    return rejectedConditionSchema;
  }

  public JsonSchemaDefinition requestValidationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromObject(requestValidationSchema));
  }

  public JsonSchemaDefinition requestVerificationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromObject(requestVerificationSchema));
  }

  public JsonSchemaDefinition responseValidationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromObject(responseValidationSchema));
  }

  public JsonSchemaDefinition rejectedConditionSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromObject(rejectedConditionSchema));
  }
}
