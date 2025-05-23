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

package org.idp.server.authentication.interactors.sms;

public enum SmsAuthenticationExecutionStatus {
  OK(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  SmsAuthenticationExecutionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }

  public boolean isClientError() {
    return this == CLIENT_ERROR;
  }

  public boolean isServerError() {
    return this == SERVER_ERROR;
  }

  public int code() {
    return statusCode;
  }
}
