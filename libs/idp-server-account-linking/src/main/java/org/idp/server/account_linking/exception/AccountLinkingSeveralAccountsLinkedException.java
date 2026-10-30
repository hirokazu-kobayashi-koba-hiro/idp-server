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
 * Raised when a provider that now allows one account still has several linked for the user, left
 * from when it allowed more, and none of them is the account being linked: which to renew cannot be
 * told.
 */
public class AccountLinkingSeveralAccountsLinkedException extends ConflictException {

  public AccountLinkingSeveralAccountsLinkedException(String message) {
    super(message);
  }
}
