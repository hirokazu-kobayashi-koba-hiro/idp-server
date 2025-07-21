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

import org.idp.server.core.extension.identity.verification.configuration.common.IdentityVerificationBasicAuthConfig;
import org.idp.server.platform.http.HmacAuthenticationConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  IdentityVerificationRequestConfig request = new IdentityVerificationRequestConfig();
  IdentityVerificationPreHookConfig preHook = new IdentityVerificationPreHookConfig();
  IdentityVerificationExecutionConfig execution = new IdentityVerificationExecutionConfig();
  IdentityVerificationPostHookConfig postHook = new IdentityVerificationPostHookConfig();
  IdentityVerificationStoreConfig store = new IdentityVerificationStoreConfig();
  IdentityVerificationResponseConfig response = new IdentityVerificationResponseConfig();
  IdentityVerificationConditionConfig rejectionCondition =
      new IdentityVerificationConditionConfig();
  IdentityVerificationConditionConfig cancellationCondition =
      new IdentityVerificationConditionConfig();
  IdentityVerificationConditionConfig completionCondition =
      new IdentityVerificationConditionConfig();

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

  public boolean hasCompletionCondition() {
    return completionCondition != null && completionCondition.exists();
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

  public IdentityVerificationConditionConfig rejectionCondition() {
    if (rejectionCondition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return rejectionCondition;
  }

  public IdentityVerificationConditionConfig cancellationCondition() {
    if (cancellationCondition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return cancellationCondition;
  }

  public IdentityVerificationConditionConfig completionCondition() {
    if (completionCondition == null) {
      return new IdentityVerificationConditionConfig();
    }
    return completionCondition;
  }

  public boolean hasOAuthAuthorization() {
    return execution.hasOAuthAuthorization();
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    return execution.oauthAuthorization();
  }

  public boolean hasHmacAuthentication() {
    return execution.hasHmacAuthentication();
  }

  public HmacAuthenticationConfiguration hmacAuthentication() {
    return execution.hmacAuthentication();
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
}
