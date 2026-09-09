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

package org.idp.server.core.openid.identity.email;

import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.resource.ResourceReader;

/**
 * Validates the {@code new_email} candidate of a self-service email change (Issue #1416).
 *
 * <p>Nothing downstream checks the shape — the sender is handed whatever string it gets — and the
 * value is committed as the user's {@code email} and, under an {@code EMAIL} identity policy, as
 * {@code preferred_username}. So the bounds are enforced here, before any mail is sent. The schema
 * also rejects non-string types, which keeps a coerced number or object out of the sender.
 */
public class EmailChangeRequestValidator {

  EmailVerificationRequest request;
  JsonSchemaValidator validator;

  public EmailChangeRequestValidator(EmailVerificationRequest request) {
    this.request = request;
    String json = ResourceReader.readClasspath("/schema/1.0/email-change-request.json");
    this.validator = JsonSchemaValidator.fromString(json);
  }

  public JsonSchemaValidationResult validate() {
    return validator.validate(request.toJsonNodeWrapper());
  }
}
