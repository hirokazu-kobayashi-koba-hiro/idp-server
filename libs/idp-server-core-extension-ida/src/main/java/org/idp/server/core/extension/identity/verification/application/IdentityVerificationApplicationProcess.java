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

package org.idp.server.core.extension.identity.verification.application;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationApplicationProcess implements JsonReadable {

  String process;
  String requestedAt;

  public IdentityVerificationApplicationProcess() {}

  public IdentityVerificationApplicationProcess(
      IdentityVerificationProcess process, LocalDateTime requestedAt) {
    this.process = process.name();
    this.requestedAt = requestedAt.toString();
  }

  public IdentityVerificationApplicationProcess(String process, String requestedAt) {
    this.process = process;
    this.requestedAt = requestedAt;
  }

  public String process() {
    return process;
  }

  public String requestedAt() {
    return requestedAt;
  }

  public Map<String, Object> toMap() {
    return Map.of("process", process, "requestedAt", requestedAt);
  }
}
