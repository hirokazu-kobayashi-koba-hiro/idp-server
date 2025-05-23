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

package org.idp.server.control_plane.management.oidc.authorization.validator;

import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.basic.json.schema.JsonSchemaValidator;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;

public class AuthorizationServerRequestValidator {

  AuthorizationServerUpdateRequest request;
  boolean dryRun;
  JsonSchemaValidator authorizationServerSchemaValidator;

  public AuthorizationServerRequestValidator(
      AuthorizationServerUpdateRequest request, boolean dryRun) {
    this.request = request;
    this.dryRun = dryRun;
    this.authorizationServerSchemaValidator =
        new JsonSchemaValidator(SchemaReader.authorizationServerSchema());
  }

  public AuthorizationServerRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(request.toMap());
    JsonSchemaValidationResult serverResult =
        authorizationServerSchemaValidator.validate(jsonNodeWrapper);

    if (!serverResult.isValid()) {
      return AuthorizationServerRequestValidationResult.error(serverResult, dryRun);
    }

    return AuthorizationServerRequestValidationResult.success(serverResult, dryRun);
  }
}
