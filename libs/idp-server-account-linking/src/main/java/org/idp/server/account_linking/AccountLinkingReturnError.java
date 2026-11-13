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
package org.idp.server.account_linking;

/**
 * The error codes a failed link is reported back to the RP with.
 *
 * <p>A fixed set. The {@code error} the external provider sends to the callback is not trusted: it
 * arrives on a URL anyone can open, so it is mapped onto this set rather than passed through.
 */
public enum AccountLinkingReturnError {
  ACCESS_DENIED("access_denied"),
  LOGIN_REQUIRED("login_required"),
  TEMPORARILY_UNAVAILABLE("temporarily_unavailable"),
  SERVER_ERROR("server_error");

  String value;

  AccountLinkingReturnError(String value) {
    this.value = value;
  }

  /** Maps the {@code error} the external provider returned to the callback. */
  public static AccountLinkingReturnError fromExternal(String error) {
    if ("access_denied".equals(error)) {
      return ACCESS_DENIED;
    }
    if ("temporarily_unavailable".equals(error)) {
      return TEMPORARILY_UNAVAILABLE;
    }
    return SERVER_ERROR;
  }

  public String value() {
    return value;
  }
}
