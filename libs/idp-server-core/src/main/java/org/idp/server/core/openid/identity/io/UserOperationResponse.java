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

package org.idp.server.core.openid.identity.io;

import java.util.Map;

public class UserOperationResponse {

  UserOperationStatus status;
  Map<String, Object> contents;

  public static UserOperationResponse success(Map<String, Object> contents) {
    return new UserOperationResponse(UserOperationStatus.OK, contents);
  }

  public UserOperationResponse(UserOperationStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static UserOperationResponse failure(Map<String, Object> contents) {
    return new UserOperationResponse(UserOperationStatus.INVALID_REQUEST, contents);
  }

  public static UserOperationResponse insufficientScope(Map<String, Object> contents) {
    return new UserOperationResponse(UserOperationStatus.FORBIDDEN, contents);
  }

  public static UserOperationResponse notFound(Map<String, Object> contents) {
    return new UserOperationResponse(UserOperationStatus.NOT_FOUND, contents);
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
