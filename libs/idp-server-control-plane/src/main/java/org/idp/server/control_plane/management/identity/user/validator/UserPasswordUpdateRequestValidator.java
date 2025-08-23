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

package org.idp.server.control_plane.management.identity.user.validator;

import org.idp.server.control_plane.base.schema.ControlPlaneV1SchemaReader;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class UserPasswordUpdateRequestValidator {

  UserRegistrationRequest request;
  boolean dryRun;
  JsonSchemaValidator userSchemaValidator;

  public UserPasswordUpdateRequestValidator(UserRegistrationRequest request, boolean dryRun) {
    this.request = request;
    this.dryRun = dryRun;
    this.userSchemaValidator =
        new JsonSchemaValidator(ControlPlaneV1SchemaReader.adminUserPasswordSchema());
  }

  public UserRequestValidationResult validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult userResult = userSchemaValidator.validate(jsonNodeWrapper);

    if (!userResult.isValid()) {
      return UserRequestValidationResult.error(userResult, dryRun);
    }

    return UserRequestValidationResult.success(userResult, dryRun);
  }
}
