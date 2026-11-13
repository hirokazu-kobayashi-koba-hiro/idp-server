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

import org.idp.server.platform.exception.ForbiddenException;

/**
 * Raised when the subject driving a linking session is not the user bound to it at link time.
 *
 * <p>This is the signal for linking CSRF in either direction: an attacker replaying a victim's
 * state, or an attacker luring a victim through the attacker's own state. Callers are expected to
 * emit a security event, because a run of these is an attack in progress rather than a user error.
 */
public class AccountLinkingOperatorMismatchException extends ForbiddenException {

  public AccountLinkingOperatorMismatchException(String message) {
    super(message);
  }
}
