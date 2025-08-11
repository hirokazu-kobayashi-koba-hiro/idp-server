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

package org.idp.server.core.openid.authentication.acr;

import java.util.*;

public class AcrResolver {

  public static String resolve(
      Map<String, List<String>> acrMappingRules, List<String> performedMethods) {

    if (acrMappingRules == null || acrMappingRules.isEmpty()) {
      return "";
    }

    List<String> acrs = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : acrMappingRules.entrySet()) {
      if (performedMethods.stream()
          .anyMatch(performedMethod -> entry.getValue().contains(performedMethod))) {
        acrs.add(entry.getKey());
      }
    }
    return acrs.stream().findFirst().orElse("");
  }
}
