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

package org.idp.server.core.extension.identity.verification.configuration.registration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationResponseConfig;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class IdentityVerificationRegistrationConfig implements JsonReadable {
  IdentityVerificationBasicAuthConfig basicAuth = new IdentityVerificationBasicAuthConfig();
  Map<String, Object> requestValidationSchema = new HashMap<>();
  Map<String, Object> requestVerificationSchema = new HashMap<>();
  IdentityVerificationResponseConfig response = new IdentityVerificationResponseConfig();

  public IdentityVerificationRegistrationConfig() {}

  public IdentityVerificationBasicAuthConfig basicAuthConfiguration() {
    if (basicAuth == null) {
      return new IdentityVerificationBasicAuthConfig();
    }
    return basicAuth;
  }

  public BasicAuth basicAuth() {
    return basicAuthConfiguration().basicAuth();
  }

  public boolean hasBasicAuth() {
    return basicAuth != null && basicAuth.exists();
  }

  public Map<String, Object> requestValidationSchema() {
    return requestValidationSchema;
  }

  public Map<String, Object> requestVerificationSchema() {
    return requestVerificationSchema;
  }

  public JsonSchemaDefinition requestValidationSchemaAsDefinition() {
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(requestValidationSchema));
  }

  /**
   * Every field, so that reading a configuration and writing it back preserves it (Issue #1900).
   *
   * <p>{@code response} was missing entirely, and {@code basic_auth} was the configuration object
   * rather than its map, which serialized to {@code {}}. Both were stored and neither came back, so
   * a read-edit-write round trip through the management API dropped them.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("basic_auth", basicAuthConfiguration().toMap());
    map.put("request_validation_schema", requestValidationSchema);
    map.put("request_verification_schema", requestVerificationSchema);
    map.put("response", response().toMap());
    return map;
  }

  public IdentityVerificationResponseConfig response() {
    if (response == null) {
      return new IdentityVerificationResponseConfig();
    }
    return response;
  }
}
