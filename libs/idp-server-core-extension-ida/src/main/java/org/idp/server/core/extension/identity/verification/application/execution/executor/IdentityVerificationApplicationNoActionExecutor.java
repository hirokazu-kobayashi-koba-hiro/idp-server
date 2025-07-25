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

package org.idp.server.core.extension.identity.verification.application.execution.executor;

import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationExecutor;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionResult;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationExecutionStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;

public class IdentityVerificationApplicationNoActionExecutor
    implements IdentityVerificationApplicationExecutor {

  @Override
  public String type() {
    return "no_action";
  }

  @Override
  public IdentityVerificationExecutionResult execute(
      IdentityVerificationContext context,
      IdentityVerificationProcess processes,
      IdentityVerificationConfiguration verificationConfiguration) {

    return new IdentityVerificationExecutionResult(
        IdentityVerificationExecutionStatus.OK, Map.of());
  }
}
