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

package org.idp.server.core.openid.identity.contact.execution;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * Registry of contact executors, keyed by {@code execution.function} (Issue #1416).
 *
 * <p>Mirrors {@code AuthenticationExecutors}. A configuration naming a function with no executor is
 * a configuration error and says so, rather than falling through to a default path — the earlier
 * shape inferred "delegated" from the absence of a local sender, so a tenant using any other
 * function reached a null sender lookup and a 500.
 */
public class ContactVerificationExecutors {

  Map<String, ContactVerificationExecutor> executors;

  public ContactVerificationExecutors(Map<String, ContactVerificationExecutor> executors) {
    this.executors = new HashMap<>(executors);
  }

  public ContactVerificationExecutor get(String function) {
    ContactVerificationExecutor executor = executors.get(function);

    if (executor == null) {
      throw new UnSupportedException(
          String.format(
              "No contact verification executor found for execution function (%s)", function));
    }

    return executor;
  }

  public boolean contains(String function) {
    return executors.containsKey(function);
  }
}
