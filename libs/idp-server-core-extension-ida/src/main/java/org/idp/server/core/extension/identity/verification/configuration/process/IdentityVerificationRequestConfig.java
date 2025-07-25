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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class IdentityVerificationRequestConfig implements JsonReadable {
  IdentityVerificationBasicAuthConfig basicAuth = new IdentityVerificationBasicAuthConfig();
  Map<String, Object> schema = new HashMap<>();

  public IdentityVerificationRequestConfig() {}

  public IdentityVerificationBasicAuthConfig basicAuth() {
    if (basicAuth == null) {
      return new IdentityVerificationBasicAuthConfig();
    }
    return basicAuth;
  }

  public boolean hasBasicAuth() {
    return basicAuth != null && basicAuth.exists();
  }

  public JsonSchemaDefinition requestSchemaAsDefinition() {
    if (schema == null) {
      return new JsonSchemaDefinition(JsonNodeWrapper.empty());
    }
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(schema));
  }
}
