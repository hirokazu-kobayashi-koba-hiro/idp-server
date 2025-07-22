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

import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationExecutionConfig implements JsonReadable {
  String type;
  IdentityVerificationHttpRequestConfig httpRequest = new IdentityVerificationHttpRequestConfig();
  IdentityVerificationMockConfig mock = new IdentityVerificationMockConfig();

  public IdentityVerificationExecutionConfig() {}

  public String type() {
    return type;
  }

  public IdentityVerificationHttpRequestConfig httpRequest() {
    if (httpRequest == null) {
      return new IdentityVerificationHttpRequestConfig();
    }
    return httpRequest;
  }

  public boolean hasHttpRequest() {
    return httpRequest != null && httpRequest.exists();
  }

  public IdentityVerificationMockConfig mock() {
    if (mock == null) {
      return new IdentityVerificationMockConfig();
    }
    return mock;
  }

  public boolean hasMock() {
    return mock != null;
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }
}
