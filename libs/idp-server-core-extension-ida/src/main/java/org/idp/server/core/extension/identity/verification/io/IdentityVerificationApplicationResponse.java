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

import java.util.Map;

public class IdentityVerificationApplicationResponse {

  IdentityVerificationApplicationStatus status;
  Map<String, Object> response;

  public static IdentityVerificationApplicationResponse OK(Map<String, Object> response) {
    return new IdentityVerificationApplicationResponse(
        IdentityVerificationApplicationStatus.OK, response);
  }

  public static IdentityVerificationApplicationResponse CLIENT_ERROR(Map<String, Object> response) {
    return new IdentityVerificationApplicationResponse(
        IdentityVerificationApplicationStatus.CLIENT_ERROR, response);
  }

  public static IdentityVerificationApplicationResponse SERVER_ERROR(Map<String, Object> response) {
    return new IdentityVerificationApplicationResponse(
        IdentityVerificationApplicationStatus.SERVER_ERROR, response);
  }

  private IdentityVerificationApplicationResponse(
      IdentityVerificationApplicationStatus status, Map<String, Object> response) {
    this.status = status;
    this.response = response;
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isOK() {
    return status == IdentityVerificationApplicationStatus.OK;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
