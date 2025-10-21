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

package org.idp.server.control_plane.management.oidc.client.validator;

import org.idp.server.control_plane.base.schema.ControlPlaneV1SchemaReader;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;

public class ClientRegistrationRequestValidator {

  ClientRegistrationRequest request;
  boolean dryRun;
  JsonSchemaValidator clientSchemaValidator;

  public ClientRegistrationRequestValidator(ClientRegistrationRequest request, boolean dryRun) {
    this.request = request;
    this.dryRun = dryRun;
    this.clientSchemaValidator = new JsonSchemaValidator(ControlPlaneV1SchemaReader.clientSchema());
  }

  public void validate() {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request.toMap());
    JsonSchemaValidationResult clientResult = clientSchemaValidator.validate(jsonNodeWrapper);

    if (!clientResult.isValid()) {
      throw new InvalidRequestException(
          "Invalid client registration request", clientResult.errors());
    }
  }
}
