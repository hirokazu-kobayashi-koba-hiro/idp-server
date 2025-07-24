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

package org.idp.server.core.extension.identity.verification.configuration.process;

import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationTransitionConfig implements JsonReadable {

  IdentityVerificationConditionConfig rejected = new IdentityVerificationConditionConfig();
  IdentityVerificationConditionConfig canceled = new IdentityVerificationConditionConfig();
  IdentityVerificationConditionConfig approved = new IdentityVerificationConditionConfig();

  public IdentityVerificationTransitionConfig() {}

  public IdentityVerificationConditionConfig rejected() {
    if (rejected == null) {
      return new IdentityVerificationConditionConfig();
    }
    return rejected;
  }

  public IdentityVerificationConditionConfig canceled() {
    if (canceled == null) {
      return new IdentityVerificationConditionConfig();
    }
    return canceled;
  }

  public IdentityVerificationConditionConfig approved() {
    if (approved == null) {
      return new IdentityVerificationConditionConfig();
    }
    return approved;
  }
}
