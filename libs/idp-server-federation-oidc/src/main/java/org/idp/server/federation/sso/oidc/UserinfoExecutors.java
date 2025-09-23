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

package org.idp.server.federation.sso.oidc;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.http.HttpRequestExecutor;

public class UserinfoExecutors {

  Map<String, UserinfoExecutor> executors;

  public UserinfoExecutors(HttpRequestExecutor httpRequestExecutor) {
    this.executors = new HashMap<>();
    UserinfoHttpRequestExecutor httpRequest = new UserinfoHttpRequestExecutor(httpRequestExecutor);
    executors.put(httpRequest.function(), httpRequest);
    UserinfoHttpRequestsExecutor httpRequests =
        new UserinfoHttpRequestsExecutor(httpRequestExecutor);
    executors.put(httpRequests.function(), httpRequests);
  }

  public UserinfoExecutor get(String function) {
    UserinfoExecutor executor = executors.get(function);

    if (executor == null) {
      throw new UnSupportedException("UserinfoExecutors Unknown function: " + function);
    }

    return executor;
  }
}
