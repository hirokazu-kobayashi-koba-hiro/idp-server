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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.configuration.post_hook.IdentityVerificationPostHookConfig;
import org.idp.server.core.extension.identity.verification.configuration.pre_hook.IdentityVerificationPreHookConfig;
import org.idp.server.platform.http.HmacAuthenticationConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationProcessConfiguration implements JsonReadable {
  Map<String, Object> requestSchema = new HashMap<>();
  IdentityVerificationPreHookConfig preHook = new IdentityVerificationPreHookConfig();
  IdentityVerificationExecutionConfig execution = new IdentityVerificationExecutionConfig();
  IdentityVerificationPostHookConfig postHook = new IdentityVerificationPostHookConfig();
  Map<String, Object> responseSchema = new HashMap<>();
  IdentityVerificationConditionConfig rejectedConditionConfiguration =
      new IdentityVerificationConditionConfig();
  IdentityVerificationConditionConfig completionConditionConfiguration =
      new IdentityVerificationConditionConfig();

  public IdentityVerificationProcessConfiguration() {}

  public JsonSchemaDefinition requestSchemaAsDefinition() {
    if (requestSchema == null) {
      return new JsonSchemaDefinition(JsonNodeWrapper.empty());
    }
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(requestSchema));
  }

  public boolean hasCompletionCondition() {
    return completionConditionConfiguration != null && !completionConditionConfiguration.exists();
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

  public IdentityVerificationConditionConfig rejectedConditionConfiguration() {
    if (rejectedConditionConfiguration == null) {
      return new IdentityVerificationConditionConfig();
    }
    return rejectedConditionConfiguration;
  }

  public IdentityVerificationConditionConfig completionConditionConfiguration() {
    if (completionConditionConfiguration == null) {
      return new IdentityVerificationConditionConfig();
    }
    return completionConditionConfiguration;
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

  public JsonSchemaDefinition responseSchemaAsDefinition() {
    if (responseSchema == null) {
      return new JsonSchemaDefinition(JsonNodeWrapper.empty());
    }
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(responseSchema));
  }
}
