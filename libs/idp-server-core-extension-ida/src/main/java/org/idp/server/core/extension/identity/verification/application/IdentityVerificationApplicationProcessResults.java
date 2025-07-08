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

import java.util.HashMap;
import java.util.Map;

public class IdentityVerificationApplicationProcessResults {

  Map<String, IdentityVerificationApplicationProcessResult> values;

  public IdentityVerificationApplicationProcessResults() {
    this.values = new HashMap<>();
  }

  public IdentityVerificationApplicationProcessResults(
      Map<String, IdentityVerificationApplicationProcessResult> values) {
    this.values = values;
  }

  public Map<String, IdentityVerificationApplicationProcessResult> toMap() {
    return values;
  }

  public Map<String, Object> toMapAsObject() {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, IdentityVerificationApplicationProcessResult> entry :
        values.entrySet()) {
      result.put(entry.getKey(), entry.getValue().toMap());
    }
    return result;
  }

  public boolean contains(String type) {
    return values.containsKey(type);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public IdentityVerificationApplicationProcessResult get(String name) {
    return values.get(name);
  }
}
