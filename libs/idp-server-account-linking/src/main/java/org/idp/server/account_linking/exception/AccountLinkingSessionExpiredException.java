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
package org.idp.server.account_linking.exception;

import org.idp.server.platform.exception.BadRequestException;

/**
 * Raised when a linking session is used after its expiry.
 *
 * <p>Nothing collided with it; the state the caller presents has simply run out, as an expired
 * authorization code or verification code does. It is answered as a bad request, apart from the
 * conflicts of a session another request already moved on.
 */
public class AccountLinkingSessionExpiredException extends BadRequestException {

  public AccountLinkingSessionExpiredException(String message) {
    super(message);
  }
}
