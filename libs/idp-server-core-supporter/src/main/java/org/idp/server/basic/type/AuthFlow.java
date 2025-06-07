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

package org.idp.server.basic.type;

import org.idp.server.platform.exception.UnSupportedException;

public enum AuthFlow {
  OAUTH("oauth"),
  CIBA("ciba"),
  MFA("mfa");

  String value;

  AuthFlow(String value) {
    this.value = value;
  }

  public static AuthFlow of(String authorizationFlow) {
    for (AuthFlow flow : AuthFlow.values()) {
      if (flow.value.equals(authorizationFlow)) {
        return flow;
      }
    }
    throw new UnSupportedException(
        String.format("unsupported authorization flow (%s)", authorizationFlow));
  }

  public String value() {
    return value;
  }
  ;
}
