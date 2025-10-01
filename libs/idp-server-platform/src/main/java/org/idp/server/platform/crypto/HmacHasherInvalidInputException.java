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

package org.idp.server.platform.crypto;

import org.idp.server.platform.exception.BadRequestException;

/**
 * Exception thrown when HmacHasher receives invalid input values.
 *
 * <p>This exception is thrown when inappropriate input values such as null or empty strings are
 * provided for HMAC hash computation.
 */
public class HmacHasherInvalidInputException extends BadRequestException {

  public HmacHasherInvalidInputException(String message) {
    super(message);
  }

  public HmacHasherInvalidInputException(String message, Throwable cause) {
    super(message, cause);
  }
}
