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

package org.idp.server.authentication.interactors.email;

import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.resource.ResourceReader;

/**
 * Validates the {@code new_email} candidate of the self-service email confirm / change challenge
 * (Issue #1416).
 *
 * <p>The candidate is committed verbatim as the user's {@code email} and, under an {@code EMAIL}
 * identity policy, as {@code preferred_username} — the login identifier. Nothing downstream checks
 * its shape (the built-in executor hands whatever string it gets to the sender), so the shape and
 * length bounds are enforced here, before any mail is sent.
 */
public class EmailChangeRequestValidator {

  AuthenticationInteractionRequest request;
  JsonSchemaValidator validator;

  public EmailChangeRequestValidator(AuthenticationInteractionRequest request) {
    this.request = request;
    String json =
        ResourceReader.readClasspath("/schema/1.0/authentication/email/email-change-request.json");
    this.validator = JsonSchemaValidator.fromString(json);
  }

  public JsonSchemaValidationResult validate() {
    return validator.validate(JsonNodeWrapper.fromObject(request.toMap()));
  }
}
