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

package org.idp.server.account_linking.io;

/** Outcome of one account linking operation, mapped to the HTTP status the API answers with. */
public enum AccountLinkingStatus {
  OK(200),
  CREATED(201),
  REDIRECT(302),
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  SERVER_ERROR(500);

  int statusCode;

  AccountLinkingStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isError() {
    return statusCode >= 400;
  }

  public boolean isRedirect() {
    return this == REDIRECT;
  }
}
