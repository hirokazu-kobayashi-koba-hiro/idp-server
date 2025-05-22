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


package org.idp.server.authentication.interactors.fidouaf.external;

import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorType;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.exception.NotFoundException;

public class ExternalFidoUafServerConfiguration implements JsonReadable {
  String type;
  String description;
  OAuthAuthorizationConfiguration oauthAuthorization;
  Map<String, ExternalFidoUafServerExecutionConfiguration> executions;

  public ExternalFidoUafServerConfiguration() {}

  public FidoUafExecutorType type() {
    return new FidoUafExecutorType(type);
  }

  public String description() {
    return description;
  }

  public OAuthAuthorizationConfiguration oauthAuthorization() {
    if (oauthAuthorization == null) {
      return new OAuthAuthorizationConfiguration();
    }
    return oauthAuthorization;
  }

  public boolean hasAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public Map<String, ExternalFidoUafServerExecutionConfiguration> executions() {
    return executions;
  }

  public ExternalFidoUafServerExecutionConfiguration getExecutionConfig(String executionType) {
    if (!executions.containsKey(executionType)) {
      throw new NotFoundException(
          "invalid configuration. type: " + executionType + " is unregistered.");
    }
    return executions.get(executionType);
  }
}
