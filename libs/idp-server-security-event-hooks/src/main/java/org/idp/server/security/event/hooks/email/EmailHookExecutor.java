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

package org.idp.server.security.event.hooks.email;

import java.net.http.HttpClient;
import java.util.Map;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.StandardSecurityEventHookType;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class EmailHookExecutor implements SecurityEventHook {

  LoggerWrapper log = LoggerWrapper.getLogger(EmailHookExecutor.class);
  HttpClient httpClient;
  JsonConverter jsonConverter;

  public EmailHookExecutor() {
    this.httpClient = HttpClientFactory.defaultClient();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SSF.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());

    return SecurityEventHookResult.success(type(), Map.of());
  }
}
