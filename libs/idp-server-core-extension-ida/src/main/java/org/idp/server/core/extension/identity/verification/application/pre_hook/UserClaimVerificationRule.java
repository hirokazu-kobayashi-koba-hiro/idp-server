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

package org.idp.server.core.extension.identity.verification.application.pre_hook;

import org.idp.server.platform.json.JsonReadable;

public class UserClaimVerificationRule implements JsonReadable {
  String operation;
  String requestJsonPath;
  String userClaimJsonPath;

  public UserClaimVerificationRule() {}

  public UserClaimVerificationRule(String requestJsonPath, String userClaimJsonPath) {
    this.requestJsonPath = requestJsonPath;
    this.userClaimJsonPath = userClaimJsonPath;
  }

  public UserClaimVerificationRule(
      String operation, String requestJsonPath, String userClaimJsonPath) {
    this.operation = operation;
    this.requestJsonPath = requestJsonPath;
    this.userClaimJsonPath = userClaimJsonPath;
  }

  /**
   * Comparison operator applied to {@code requestValue} (target) against {@code userValue}
   * (expected). Defaults to {@code eq} when omitted, preserving the original exact-match behavior.
   * See {@link org.idp.server.platform.condition.ConditionOperation}.
   */
  public String operation() {
    return operation;
  }

  public boolean hasOperation() {
    return operation != null && !operation.isEmpty();
  }

  public String requestJsonPath() {
    return requestJsonPath;
  }

  public String userClaimJsonPath() {
    return userClaimJsonPath;
  }
}
