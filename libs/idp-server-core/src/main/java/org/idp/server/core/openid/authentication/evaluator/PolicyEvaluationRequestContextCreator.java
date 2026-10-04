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

package org.idp.server.core.openid.authentication.evaluator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationContext;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;

/**
 * Creates the {@code $.request.*} node of the authentication policy evaluation context: what the
 * authorization request asked for (Issue #1907).
 *
 * <p>Holds {@code custom_params}, limited to the sources the policy trusts ({@code
 * custom_params_trusted_sources}). A value from any other source is left out, so a condition reads
 * it as missing; the end-user could have changed it on the way.
 */
public class PolicyEvaluationRequestContextCreator {

  /**
   * @return the request node, or an empty map when there is nothing to show
   */
  public static Map<String, Object> create(
      AuthenticationContext context, AuthenticationPolicy policy) {
    Map<String, Object> map = new HashMap<>();
    if (context == null || policy == null) {
      return map;
    }
    Map<String, String> customParams =
        context.customParams().valuesFrom(policy.trustedCustomParamSources());
    if (!customParams.isEmpty()) {
      map.put("custom_params", customParams);
    }
    return map;
  }
}
