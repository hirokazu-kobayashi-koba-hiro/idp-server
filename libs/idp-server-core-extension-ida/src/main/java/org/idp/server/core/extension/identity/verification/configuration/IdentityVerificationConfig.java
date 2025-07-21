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

package org.idp.server.core.extension.identity.verification.configuration;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationConfig implements JsonReadable {
  String type;
  Map<String, Object> details;

  public IdentityVerificationConfig() {}

  public IdentityVerificationConfig(String type, Map<String, Object> details) {
    this.type = type;
    this.details = details;
  }

  public String type() {
    return type;
  }

  public Map<String, Object> details() {
    return details;
  }
}
