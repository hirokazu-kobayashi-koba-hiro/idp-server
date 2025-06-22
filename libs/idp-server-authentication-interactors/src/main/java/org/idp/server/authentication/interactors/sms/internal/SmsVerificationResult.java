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

package org.idp.server.authentication.interactors.sms.internal;

import java.util.Map;

public class SmsVerificationResult {

  boolean result;
  Map<String, Object> response;

  public static SmsVerificationResult success(Map<String, Object> response) {
    return new SmsVerificationResult(true, response);
  }

  public static SmsVerificationResult failure(Map<String, Object> response) {
    return new SmsVerificationResult(false, response);
  }

  public SmsVerificationResult(boolean result, Map<String, Object> response) {
    this.result = result;
    this.response = response;
  }

  public boolean isSuccess() {
    return result;
  }

  public boolean isFailure() {
    return !result;
  }

  public Map<String, Object> response() {
    return response;
  }
}
