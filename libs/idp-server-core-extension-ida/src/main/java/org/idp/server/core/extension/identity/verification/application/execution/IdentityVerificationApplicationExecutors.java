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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.execution.executor.IdentityVerificationApplicationHttpRequestExecutor;
import org.idp.server.core.extension.identity.verification.application.execution.executor.IdentityVerificationApplicationNoActionExecutor;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;

public class IdentityVerificationApplicationExecutors {

  Map<String, IdentityVerificationApplicationExecutor> executors;
  LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerificationApplicationExecutors.class);

  public IdentityVerificationApplicationExecutors() {
    this.executors = new HashMap<>();
    IdentityVerificationApplicationHttpRequestExecutor httpRequestExecutor =
        new IdentityVerificationApplicationHttpRequestExecutor();
    this.executors.put(httpRequestExecutor.type(), httpRequestExecutor);
    IdentityVerificationApplicationNoActionExecutor noActionExecutor =
        new IdentityVerificationApplicationNoActionExecutor();
    this.executors.put(noActionExecutor.type(), noActionExecutor);
  }

  public IdentityVerificationApplicationExecutor get(String name) {
    IdentityVerificationApplicationExecutor executor = executors.get(name);

    log.info("IdentityVerificationApplicationExecutor for name: {}", name);

    if (executor == null) {
      throw new UnSupportedException(
          "Unsupported IdentityVerificationApplicationExecutor: " + name);
    }

    return executor;
  }
}
