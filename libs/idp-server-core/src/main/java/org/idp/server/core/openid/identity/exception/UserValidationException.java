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

package org.idp.server.core.openid.identity.exception;

import org.idp.server.platform.exception.BadRequestException;

/**
 * Exception thrown when user data fails validation rules.
 *
 * <p>This exception indicates validation errors such as missing required fields or invalid field
 * formats.
 */
public class UserValidationException extends BadRequestException {
  public UserValidationException(String message) {
    super(message);
  }
}
