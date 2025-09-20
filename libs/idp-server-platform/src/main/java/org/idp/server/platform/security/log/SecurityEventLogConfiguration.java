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

package org.idp.server.platform.security.log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;

public class SecurityEventLogConfiguration {

  private final TenantAttributes tenantAttributes;

  public SecurityEventLogConfiguration(TenantAttributes tenantAttributes) {
    this.tenantAttributes = tenantAttributes;
  }

  public SecurityEventLogFormatter.Format getFormat() {
    String formatValue =
        tenantAttributes.optValueAsString("security_event_log_format", "structured_json");
    return SecurityEventLogFormatter.Format.fromValue(formatValue);
  }

  public boolean isEnabled() {
    return getFormat() != SecurityEventLogFormatter.Format.DISABLED;
  }

  public boolean isDebugEnabled() {
    return tenantAttributes.optValueAsBoolean("security_event_debug_logging", false);
  }

  public boolean isStageEnabled(String stage) {
    String enabledStages =
        tenantAttributes.optValueAsString("security_event_log_stage", "processed");
    return enabledStages.equals("both") || enabledStages.equals(stage);
  }

  public boolean includeUserId() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_user_id", true);
  }

  public boolean includeUserExSub() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_user_ex_sub", true);
  }

  public boolean includeClientId() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_client_id", true);
  }

  public boolean includeIpAddress() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_ip", true);
  }

  public boolean includeUserAgent() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_user_agent", true);
  }

  public boolean includeEventDetail() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_detail", false);
  }

  public boolean includeUserDetail() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_user_detail", false);
  }

  public boolean includeUserPii() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_user_pii", false);
  }

  public List<String> getAllowedUserPiiKeys() {
    String allowedKeysValue =
        tenantAttributes.optValueAsString("security_event_log_allowed_user_pii_keys", "");

    if (allowedKeysValue.isEmpty()) {
      return List.of(); // デフォルトでは何も許可しない
    }

    return Arrays.asList(allowedKeysValue.split(","));
  }

  public boolean hasAllowedUserPiiKeys() {
    return !getAllowedUserPiiKeys().isEmpty();
  }

  public boolean includeTraceContext() {
    return tenantAttributes.optValueAsBoolean("security_event_log_include_trace_context", false);
  }

  public String getServiceName() {
    return tenantAttributes.optValueAsString("security_event_log_service_name", "idp-server");
  }

  public List<String> getCustomTags() {
    String tagsValue = tenantAttributes.optValueAsString("security_event_log_custom_tags", "");
    if (tagsValue.isEmpty()) {
      return List.of();
    }
    return Arrays.asList(tagsValue.split(","));
  }

  public boolean hasCustomTags() {
    return !getCustomTags().isEmpty();
  }

  public boolean isTracingEnabled() {
    return tenantAttributes.optValueAsBoolean("security_event_log_tracing_enabled", false);
  }

  public boolean isPersistenceEnabled() {
    return tenantAttributes.optValueAsBoolean("security_event_log_persistence_enabled", false);
  }

  public List<String> getDetailScrubKeys() {
    String scrubKeysValue =
        tenantAttributes.optValueAsString("security_event_log_detail_scrub_keys", "");

    // Always ensure essential security and PII keys are included
    List<String> essentialKeys =
        List.of(
            "authorization",
            "cookie",
            "password",
            "secret",
            "token",
            "access_token",
            "refresh_token",
            "api_key",
            "api_secret");

    if (scrubKeysValue.isEmpty()) {
      return essentialKeys;
    }

    List<String> configuredKeys = Arrays.asList(scrubKeysValue.split(","));
    Set<String> allKeys = new HashSet<>(configuredKeys);
    allKeys.addAll(essentialKeys);

    return new ArrayList<>(allKeys);
  }

  public boolean hasDetailScrubKeys() {
    return !getDetailScrubKeys().isEmpty();
  }
}
