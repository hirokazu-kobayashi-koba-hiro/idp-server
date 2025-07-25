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

package org.idp.server.core.extension.identity.verification.application.execution;

import java.util.Map;

public class IdentityVerificationExecutionResult {
  IdentityVerificationExecutionStatus status;
  Map<String, Object> result;

  public IdentityVerificationExecutionResult() {}

  public IdentityVerificationExecutionResult(
      IdentityVerificationExecutionStatus status, Map<String, Object> result) {
    this.status = status;
    this.result = result;
  }

  public boolean isOk() {
    return status.isOk();
  }

  public boolean isClientError() {
    return status.isClientError();
  }

  public boolean isServerError() {
    return status.isServerError();
  }

  public Map<String, Object> result() {
    return result;
  }
}
