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

package org.idp.server.core.oidc.identity.device;

import org.idp.server.core.oidc.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.resource.ResourceReader;

public class AuthenticationDevicePatchValidator {
  AuthenticationDevicePatchRequest request;
  JsonSchemaValidator validator;

  public AuthenticationDevicePatchValidator(AuthenticationDevicePatchRequest request) {
    this.request = request;
    String json = ResourceReader.readClasspath("/schema/1.0/authentication-device-patch.json");
    this.validator = JsonSchemaValidator.fromString(json);
  }

  public JsonSchemaValidationResult validate() {
    return validator.validate(request.jsonNodeWrapper());
  }
}
