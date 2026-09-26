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
package org.idp.server.platform.jose;

/**
 * The payload of a JWT is not a valid claims set: not a JSON object, or a registered claim of the
 * wrong type.
 *
 * <p>Unchecked so that {@code claims()} keeps its signature for its many callers, and typed so that
 * the boundaries that receive a JWT from outside can tell a malformed token from a bug. Left as a
 * bare {@code RuntimeException}, a token whose payload an attacker makes unparseable surfaces as a
 * 500 carrying the parser's message, before the client has authenticated.
 */
public class JsonWebTokenClaimsInvalidException extends RuntimeException {

  public JsonWebTokenClaimsInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
