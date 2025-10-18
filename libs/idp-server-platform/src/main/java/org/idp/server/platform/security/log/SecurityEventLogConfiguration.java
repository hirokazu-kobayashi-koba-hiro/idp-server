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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SecurityEventLogConfiguration {

  private final SecurityEventLogFormatter.Format format;
  private final boolean debugEnabled;
  private final String stage;
  private final boolean includeUserId;
  private final boolean includeUserExSub;
  private final boolean includeClientId;
  private final boolean includeIpAddress;
  private final boolean includeUserAgent;
  private final boolean includeEventDetail;
  private final boolean includeUserDetail;
  private final boolean includeUserPii;
  private final List<String> allowedUserPiiKeys;
  private final boolean includeTraceContext;
  private final String serviceName;
  private final List<String> customTags;
  private final boolean tracingEnabled;
  private final boolean persistenceEnabled;
  private final List<String> detailScrubKeys;

  private static final List<String> ESSENTIAL_SCRUB_KEYS =
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

  public SecurityEventLogConfiguration() {
    this.format = SecurityEventLogFormatter.Format.STRUCTURED_JSON;
    this.debugEnabled = false;
    this.stage = "processed";
    this.includeUserId = true;
    this.includeUserExSub = true;
    this.includeClientId = true;
    this.includeIpAddress = true;
    this.includeUserAgent = true;
    this.includeEventDetail = false;
    this.includeUserDetail = false;
    this.includeUserPii = false;
    this.allowedUserPiiKeys = List.of();
    this.includeTraceContext = false;
    this.serviceName = "idp-server";
    this.customTags = List.of();
    this.tracingEnabled = false;
    this.persistenceEnabled = false;
    this.detailScrubKeys = ESSENTIAL_SCRUB_KEYS;
  }

  public SecurityEventLogConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.format =
        SecurityEventLogFormatter.Format.fromValue(
            extractString(safeValues, "format", "structured_json"));
    this.debugEnabled = extractBoolean(safeValues, "debug_logging", false);
    this.stage = extractString(safeValues, "stage", "processed");
    this.includeUserId = extractBoolean(safeValues, "include_user_id", true);
    this.includeUserExSub = extractBoolean(safeValues, "include_user_ex_sub", true);
    this.includeClientId = extractBoolean(safeValues, "include_client_id", true);
    this.includeIpAddress = extractBoolean(safeValues, "include_ip_address", true);
    this.includeUserAgent = extractBoolean(safeValues, "include_user_agent", true);
    this.includeEventDetail = extractBoolean(safeValues, "include_event_detail", false);
    this.includeUserDetail = extractBoolean(safeValues, "include_user_detail", false);
    this.includeUserPii = extractBoolean(safeValues, "include_user_pii", false);
    this.allowedUserPiiKeys = extractCommaSeparatedList(safeValues, "allowed_user_pii_keys");
    this.includeTraceContext = extractBoolean(safeValues, "include_trace_context", false);
    this.serviceName = extractString(safeValues, "service_name", "idp-server");
    this.customTags = extractCommaSeparatedList(safeValues, "custom_tags");
    this.tracingEnabled = extractBoolean(safeValues, "tracing_enabled", false);
    this.persistenceEnabled = extractBoolean(safeValues, "persistence_enabled", false);
    this.detailScrubKeys =
        mergeWithEssentialKeys(extractCommaSeparatedList(safeValues, "detail_scrub_keys"));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("format", format.value());
    map.put("debug_logging", debugEnabled);
    map.put("stage", stage);
    map.put("include_user_id", includeUserId);
    map.put("include_user_ex_sub", includeUserExSub);
    map.put("include_client_id", includeClientId);
    map.put("include_ip_address", includeIpAddress);
    map.put("include_user_agent", includeUserAgent);
    map.put("include_event_detail", includeEventDetail);
    map.put("include_user_detail", includeUserDetail);
    map.put("include_user_pii", includeUserPii);
    map.put("allowed_user_pii_keys", String.join(",", allowedUserPiiKeys));
    map.put("include_trace_context", includeTraceContext);
    map.put("service_name", serviceName);
    map.put("custom_tags", String.join(",", customTags));
    map.put("tracing_enabled", tracingEnabled);
    map.put("persistence_enabled", persistenceEnabled);
    map.put("detail_scrub_keys", String.join(",", detailScrubKeys));
    return map;
  }

  public SecurityEventLogFormatter.Format getFormat() {
    return format;
  }

  public boolean isEnabled() {
    return format != SecurityEventLogFormatter.Format.DISABLED;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public boolean isStageEnabled(String checkStage) {
    return stage.equals("both") || stage.equals(checkStage);
  }

  public boolean includeUserId() {
    return includeUserId;
  }

  public boolean includeUserExSub() {
    return includeUserExSub;
  }

  public boolean includeClientId() {
    return includeClientId;
  }

  public boolean includeIpAddress() {
    return includeIpAddress;
  }

  public boolean includeUserAgent() {
    return includeUserAgent;
  }

  public boolean includeEventDetail() {
    return includeEventDetail;
  }

  public boolean includeUserDetail() {
    return includeUserDetail;
  }

  public boolean includeUserPii() {
    return includeUserPii;
  }

  public List<String> getAllowedUserPiiKeys() {
    return allowedUserPiiKeys;
  }

  public boolean hasAllowedUserPiiKeys() {
    return !allowedUserPiiKeys.isEmpty();
  }

  public boolean includeTraceContext() {
    return includeTraceContext;
  }

  public String getServiceName() {
    return serviceName;
  }

  public List<String> getCustomTags() {
    return customTags;
  }

  public boolean hasCustomTags() {
    return !customTags.isEmpty();
  }

  public boolean isTracingEnabled() {
    return tracingEnabled;
  }

  public boolean isPersistenceEnabled() {
    return persistenceEnabled;
  }

  public List<String> getDetailScrubKeys() {
    return detailScrubKeys;
  }

  public boolean hasDetailScrubKeys() {
    return !detailScrubKeys.isEmpty();
  }

  private static String extractString(Map<String, Object> values, String key, String defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private static boolean extractBoolean(
      Map<String, Object> values, String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }

  private static List<String> extractCommaSeparatedList(Map<String, Object> values, String key) {
    String value = extractString(values, key, "");
    if (value.isEmpty()) {
      return List.of();
    }
    return Arrays.asList(value.split(","));
  }

  private static List<String> mergeWithEssentialKeys(List<String> configuredKeys) {
    if (configuredKeys.isEmpty()) {
      return ESSENTIAL_SCRUB_KEYS;
    }
    Set<String> allKeys = new HashSet<>(configuredKeys);
    allKeys.addAll(ESSENTIAL_SCRUB_KEYS);
    return new ArrayList<>(allKeys);
  }
}
