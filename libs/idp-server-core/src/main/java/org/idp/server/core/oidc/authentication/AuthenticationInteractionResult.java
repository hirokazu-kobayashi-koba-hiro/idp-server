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


package org.idp.server.core.oidc.authentication;

import org.idp.server.core.oidc.federation.FederationInteractionResult;

public class AuthenticationInteractionResult {

  int callCount;
  int successCount;
  int failureCount;

  public AuthenticationInteractionResult() {}

  public AuthenticationInteractionResult(int callCount, int successCount, int failureCount) {
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

  public AuthenticationInteractionResult updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(
        callCount + 1, successCount + increaseSuccessCount, failureCount + increaseFailureCount);
  }

  public AuthenticationInteractionResult updateWith(
      FederationInteractionResult interactionRequestResult) {
    int increaseSuccessCount = interactionRequestResult.isSuccess() ? 1 : 0;
    int increaseFailureCount = interactionRequestResult.isSuccess() ? 0 : 1;

    return new AuthenticationInteractionResult(
        callCount + 1, successCount + increaseSuccessCount, failureCount + increaseFailureCount);
  }
}
