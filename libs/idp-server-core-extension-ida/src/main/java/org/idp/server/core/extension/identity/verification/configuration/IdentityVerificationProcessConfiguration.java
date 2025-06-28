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

package org.idp.server.core.extension.identity.verification.configuration;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  String url;
  String method;
  Map<String, String> headers;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;
  Map<String, Object> requestValidationSchema;
  Map<String, Object> requestVerificationSchema;
  Map<String, Object> requestAdditionalParameterSchema;
  Map<String, Object> responseValidationSchema;
  Map<String, Object> rejectedConditionSchema;

  public IdentityVerificationProcessConfiguration() {}

  public HttpRequestUrl httpRequestUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod httpMethod() {
    return HttpMethod.valueOf(method);
  }

  public HttpRequestStaticHeaders httpRequestHeaders() {
    return new HttpRequestStaticHeaders(headers);
  }

  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  public HttpRequestStaticBody httpRequestStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }

  public Map<String, Object> requestValidationSchema() {
    return requestValidationSchema;
  }

  public Map<String, Object> requestVerificationSchema() {
    return requestVerificationSchema;
  }

  public Map<String, Object> requestAdditionalParameterSchema() {
    return requestAdditionalParameterSchema;
  }

  public Map<String, Object> responseValidationSchema() {
    return responseValidationSchema;
  }

  public Map<String, Object> rejectedConditionSchema() {
    return rejectedConditionSchema;
  }

  public JsonSchemaDefinition requestValidationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(requestValidationSchema));
  }

  public JsonSchemaDefinition responseValidationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(responseValidationSchema));
  }
}
