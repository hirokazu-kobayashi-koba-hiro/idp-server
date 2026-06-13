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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.history.HistoryFilter;
import org.idp.server.core.extension.identity.verification.application.history.HistoryQueryPlan;
import org.idp.server.core.extension.identity.verification.application.history.IdentityVerificationHistoryConfig;
import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.platform.http.HmacAuthenticationConfig;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationProcessConfiguration implements JsonReadable {

  // Must match DenyDuplicateIdentityVerificationApplicationVerifier.type(); it is the only verifier
  // that consumes the past-application read model. When a new verifier starts consuming it, update
  // this detection (or generalize it from the consuming-verifier declaration — see #1268 / Phase
  // 2).
  private static final String DUPLICATE_APPLICATION_VERIFIER = "duplicate_application";

  IdentityVerificationRequestConfig request = new IdentityVerificationRequestConfig();
  IdentityVerificationHistoryConfig history = new IdentityVerificationHistoryConfig();
  IdentityVerificationPreHookConfig preHook = new IdentityVerificationPreHookConfig();
  IdentityVerificationExecutionConfig execution = new IdentityVerificationExecutionConfig();
  IdentityVerificationPostHookConfig postHook = new IdentityVerificationPostHookConfig();
  IdentityVerificationTransitionConfig transition = new IdentityVerificationTransitionConfig();
  IdentityVerificationStoreConfig store = new IdentityVerificationStoreConfig();
  IdentityVerificationResponseConfig response = new IdentityVerificationResponseConfig();
  ProcessDependencies dependencies = new ProcessDependencies();

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

  public IdentityVerificationHistoryConfig history() {
    if (history == null) {
      return new IdentityVerificationHistoryConfig();
    }
    return history;
  }

  /**
   * Build the history fetch plan for the given request type.
   *
   * <p>The explicit {@code history} filters (if any) are always honored. Additionally, when this
   * process runs a {@code duplicate_application} verifier, a "running-of-request-type" filter is
   * <b>merged in</b> (not used only as an empty-config fallback). This guarantees the duplicate
   * check always observes the running state of the requested type — it cannot be silently disabled
   * by an absent {@code history} section, nor by an explicit {@code history} section that happens
   * not to cover this type. Duplicate filters are de-duplicated by {@link HistoryQueryPlan#from}.
   */
  public HistoryQueryPlan historyPlan(IdentityVerificationType type) {
    List<HistoryFilter> filters = new ArrayList<>(history().filters());
    if (requiresDuplicateApplicationCheck()) {
      filters.add(HistoryFilter.running(type.name()));
    }
    return HistoryQueryPlan.from(filters);
  }

  private boolean requiresDuplicateApplicationCheck() {
    return preHook().verifications().stream()
        .anyMatch(verification -> DUPLICATE_APPLICATION_VERIFIER.equals(verification.type()));
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

  public IdentityVerificationConditionConfig applied() {
    if (transition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return transition.applied();
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

  public ProcessDependencies dependencies() {
    if (dependencies == null) {
      return new ProcessDependencies();
    }
    return dependencies;
  }

  public boolean hasDependencies() {
    return dependencies != null
        && (dependencies.hasRequiredProcesses() || dependencies.hasStatusRestrictions());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (request != null) map.put("request", request.toMap());
    if (history != null) map.put("history", history.toMap());
    if (preHook != null) map.put("pre_hook", preHook.toMap());
    if (execution != null) map.put("execution", execution.toMap());
    if (postHook != null) map.put("post_hook", postHook.toMap());
    if (transition != null) map.put("transition", transition.toMap());
    if (store != null) map.put("store", store.toMap());
    if (response != null) map.put("response", response.toMap());
    if (dependencies != null) map.put("dependencies", dependencies.toMap());
    return map;
  }
}
