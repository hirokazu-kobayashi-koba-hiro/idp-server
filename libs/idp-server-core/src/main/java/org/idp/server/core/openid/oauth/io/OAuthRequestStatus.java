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

package org.idp.server.core.openid.oauth.io;

/** OAuthRequestStatus */
public enum OAuthRequestStatus {
  OK,
  OK_SESSION_ENABLE,
  NO_INTERACTION_OK,
  OK_ACCOUNT_CREATION,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isSuccess() {
    return this == OK
        || this == OK_SESSION_ENABLE
        || this == NO_INTERACTION_OK
        || this == OK_ACCOUNT_CREATION;
  }
}
