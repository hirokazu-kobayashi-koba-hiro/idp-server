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

import org.idp.server.platform.exception.ConflictException;

/**
 * Raised when a linking session is expired, or is not in the status a transition requires.
 *
 * <p>Callers MUST NOT exchange the authorization code after this is raised: losing the race means
 * another request already owns the transition.
 */
public class AccountLinkingSessionStateException extends ConflictException {

  public AccountLinkingSessionStateException(String message) {
    super(message);
  }
}
