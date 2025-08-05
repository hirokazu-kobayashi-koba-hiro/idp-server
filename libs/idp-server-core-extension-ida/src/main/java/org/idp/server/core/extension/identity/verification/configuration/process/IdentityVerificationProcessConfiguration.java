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
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.platform.http.HmacAuthenticationConfig;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  IdentityVerificationRequestConfig request = new IdentityVerificationRequestConfig();
  IdentityVerificationPreHookConfig preHook = new IdentityVerificationPreHookConfig();
  IdentityVerificationExecutionConfig execution = new IdentityVerificationExecutionConfig();
  IdentityVerificationPostHookConfig postHook = new IdentityVerificationPostHookConfig();
  IdentityVerificationTransitionConfig transition = new IdentityVerificationTransitionConfig();
  IdentityVerificationStoreConfig store = new IdentityVerificationStoreConfig();
  IdentityVerificationResponseConfig response = new IdentityVerificationResponseConfig();

  public IdentityVerificationProcessConfiguration() {}

  public boolean hasBasicAuth() {
    if (request == null) {
      return false;
    }
    return request.hasBasicAuth();
  }

  public IdentityVerificationBasicAuthConfig basicAuthConfig() {
    if (request == null) {
      return new IdentityVerificationBasicAuthConfig();
    }
    return request.basicAuth();
  }

  public JsonSchemaDefinition requestSchemaAsDefinition() {
    if (request == null) {
      return new JsonSchemaDefinition(JsonNodeWrapper.empty());
    }
    return request.requestSchemaAsDefinition();
  }

  public IdentityVerificationPreHookConfig preHook() {
    if (preHook == null) {
      return new IdentityVerificationPreHookConfig();
    }
    return preHook;
  }

  public IdentityVerificationExecutionConfig execution() {
    return execution;
  }

  public IdentityVerificationPostHookConfig postHook() {
    if (postHook == null) {
      return new IdentityVerificationPostHookConfig();
    }
    return postHook;
  }

  public IdentityVerificationConditionConfig rejected() {
    if (transition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return transition.rejected();
  }

  public IdentityVerificationConditionConfig canceled() {
    if (transition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return transition.canceled();
  }

  public IdentityVerificationConditionConfig approved() {
    if (transition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return transition.approved();
  }

  public boolean hasOAuthAuthorization() {
    if (execution == null || !execution.hasHttpRequest()) {
      return false;
    }
    return execution.httpRequest().hasOAuthAuthorization();
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (execution == null || !execution.hasHttpRequest()) {
      return new OAuthAuthorizationConfiguration();
    }
    return execution.httpRequest().oauthAuthorization();
  }

  public boolean hasHmacAuthentication() {
    if (execution == null || !execution.hasHttpRequest()) {
      return false;
    }
    return execution.httpRequest().hasHmacAuthentication();
  }

  public HmacAuthenticationConfig hmacAuthentication() {
    if (execution == null || !execution.hasHttpRequest()) {
      return new HmacAuthenticationConfig();
    }
    return execution.httpRequest().hmacAuthentication();
  }

  public IdentityVerificationStoreConfig store() {
    if (store == null) {
      return new IdentityVerificationStoreConfig();
    }
    return store;
  }

  public IdentityVerificationResponseConfig response() {
    if (response == null) {
      return new IdentityVerificationResponseConfig();
    }
    return response;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (request != null) map.put("request", request.toMap());
    if (preHook != null) map.put("pre_hook", preHook.toMap());
    if (execution != null) map.put("execution", execution.toMap());
    if (postHook != null) map.put("post_hook", postHook.toMap());
    if (transition != null) map.put("transition", transition.toMap());
    if (store != null) map.put("store", store.toMap());
    if (response != null) map.put("response", response.toMap());
    return map;
  }
}
