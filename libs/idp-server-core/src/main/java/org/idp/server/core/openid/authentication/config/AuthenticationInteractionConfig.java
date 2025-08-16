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

package org.idp.server.core.openid.authentication.config;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationInteractionConfig implements JsonReadable {
  AuthenticationRequestConfig request = new AuthenticationRequestConfig();
  AuthenticationPreHookConfig preHook = new AuthenticationPreHookConfig();
  AuthenticationExecutionConfig execution = new AuthenticationExecutionConfig();
  AuthenticationResultConfig userResolve = new AuthenticationResultConfig();
  AuthenticationPostHookConfig postHook = new AuthenticationPostHookConfig();
  AuthenticationResponseConfig response = new AuthenticationResponseConfig();

  public AuthenticationInteractionConfig() {}

  public AuthenticationRequestConfig request() {
    if (request == null) {
      return new AuthenticationRequestConfig();
    }
    return request;
  }

  public AuthenticationPreHookConfig preHook() {
    if (preHook == null) {
      return new AuthenticationPreHookConfig();
    }
    return preHook;
  }

  public AuthenticationExecutionConfig execution() {
    if (execution == null) {
      return new AuthenticationExecutionConfig();
    }
    return execution;
  }

  public AuthenticationPostHookConfig postHook() {
    if (postHook == null) {
      return new AuthenticationPostHookConfig();
    }
    return postHook;
  }

  public AuthenticationResultConfig userResolve() {
    if (userResolve == null) {
      return new AuthenticationResultConfig();
    }
    return userResolve;
  }

  public AuthenticationResponseConfig response() {
    if (response == null) {
      return new AuthenticationResponseConfig();
    }
    return response;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("request", request().toMap());
    map.put("pre_hook", preHook().toMap());
    map.put("execution", execution().toMap());
    map.put("post_hook", postHook().toMap());
    map.put("user_resolve", userResolve().toMap());
    map.put("response", response().toMap());
    return map;
  }
}
