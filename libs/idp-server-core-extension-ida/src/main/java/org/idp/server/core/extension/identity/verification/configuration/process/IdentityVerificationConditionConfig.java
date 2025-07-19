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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationConditionConfig implements JsonReadable {

  IdentityVerificationRequestConditionConfig requestCondition =
      new IdentityVerificationRequestConditionConfig();
  IdentityVerificationProcessConditionConfig processCondition =
      new IdentityVerificationProcessConditionConfig();

  public boolean exists() {
    return hasRequestCondition() || hasProcessCondition();
  }

  public boolean hasRequestCondition() {
    return requestCondition != null && requestCondition.exists();
  }

  public IdentityVerificationRequestConditionConfig requestCondition() {
    return requestCondition;
  }

  public boolean hasProcessCondition() {
    return processCondition != null && processCondition.exists();
  }

  public IdentityVerificationProcessConditionConfig processCondition() {
    return processCondition;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    if (hasRequestCondition()) map.put("request_condition", requestCondition.toMap());
    if (hasProcessCondition()) map.put("process_condition", processCondition.toMap());
    return map;
  }
}
