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

package org.idp.server.core.extension.identity.verification.io;

public enum IdentityVerificationApplicationStatus {
  OK(200),
  CLIENT_ERROR(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  TOO_MANY_REQUESTS(429),
  SERVER_ERROR(500),
  SERVICE_UNAVAILABLE(503),
  GATEWAY_TIMEOUT(504);

  int statusCode;

  IdentityVerificationApplicationStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public static IdentityVerificationApplicationStatus fromStatusCode(int statusCode) {
    for (IdentityVerificationApplicationStatus status : values()) {
      if (status.statusCode == statusCode) {
        return status;
      }
    }
    if (statusCode >= 400 && statusCode < 500) {
      return CLIENT_ERROR;
    }

    return SERVER_ERROR;
  }
}
