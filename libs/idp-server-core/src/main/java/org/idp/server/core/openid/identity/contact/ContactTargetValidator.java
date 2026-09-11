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

package org.idp.server.core.openid.identity.contact;

import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.resource.ResourceReader;

/**
 * Validates the {@code new_value} candidate of a self-service contact change (Issue #1416).
 *
 * <p>Nothing downstream checks the shape — the sender is handed whatever string it gets — and the
 * value is committed as the user's contact attribute and, under a matching identity policy, as
 * {@code preferred_username}. So the bounds are enforced here, before anything is sent. The schema
 * also rejects non-string types, which keeps a coerced number or object out of the sender.
 *
 * <p>The rules differ per channel and live in the schema each {@link ContactChannel} names. Phone
 * is deliberately loose: normalisation (E.164 vs national notation) decides identifier equality and
 * is the same open question as email case-sensitivity, tracked separately. What is enforced here is
 * only what makes a value safe to hand to a sender.
 */
public class ContactTargetValidator {

  ContactVerificationRequest request;
  JsonSchemaValidator validator;

  public ContactTargetValidator(
      ContactVerificationRequest request, ContactVerificationOperation operation) {
    this.request = request;
    String json = ResourceReader.readClasspath(operation.targetSchemaPath());
    this.validator = JsonSchemaValidator.fromString(json);
  }

  public JsonSchemaValidationResult validate() {
    return validator.validate(request.toJsonNodeWrapper());
  }
}
