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

package org.idp.server.core.openid.authentication.loa;

import java.util.*;

public class LoaDeniedScopeResolver {

  public static List<String> resolve(
      Map<String, List<String>> levelOfAuthenticationScopes, List<String> performedMethods) {
    if (levelOfAuthenticationScopes == null || levelOfAuthenticationScopes.isEmpty()) {
      return List.of();
    }

    List<String> deniedScopes = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : levelOfAuthenticationScopes.entrySet()) {

      String scope = entry.getKey();
      List<String> requiredMethods = entry.getValue();

      if (requiredMethods.stream().noneMatch(performedMethods::contains)) {
        deniedScopes.add(scope);
      }
    }

    return deniedScopes;
  }
}
