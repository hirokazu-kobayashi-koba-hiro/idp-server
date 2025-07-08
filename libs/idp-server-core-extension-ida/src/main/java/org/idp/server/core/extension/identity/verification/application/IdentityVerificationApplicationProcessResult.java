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

package org.idp.server.core.extension.identity.verification.application;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplyingResult;

public class IdentityVerificationApplicationProcessResult {

  int callCount;
  int successCount;
  int failureCount;

  public IdentityVerificationApplicationProcessResult() {}

  public IdentityVerificationApplicationProcessResult(
      int callCount, int successCount, int failureCount) {
    this.callCount = callCount;
    this.successCount = successCount;
    this.failureCount = failureCount;
  }

  public int callCount() {
    return callCount;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public IdentityVerificationApplicationProcessResult updateWith(
      ExternalIdentityVerificationApplyingResult applyingResult) {
    int increaseSuccessCount = applyingResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = applyingResult.isSuccess() ? 0 : 1;

    return new IdentityVerificationApplicationProcessResult(
        callCount + 1, successCount + increaseSuccessCount, failureCount + increaseFailureCount);
  }

  public IdentityVerificationApplicationProcessResult updateSuccess() {

    return new IdentityVerificationApplicationProcessResult(
        callCount + 1, successCount + 1, failureCount);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("call_count", callCount);
    map.put("success_count", successCount);
    map.put("failure_count", failureCount);
    return map;
  }
}
