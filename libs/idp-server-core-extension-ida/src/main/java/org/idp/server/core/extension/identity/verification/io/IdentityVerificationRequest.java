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

package org.idp.server.core.extension.identity.verification.io;

import java.util.Map;
import java.util.function.BiConsumer;
import org.idp.server.core.oidc.identity.UserIdentifier;

public class IdentityVerificationRequest {
  Map<String, Object> values;

  public IdentityVerificationRequest() {}

  public IdentityVerificationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean getValueAsBoolean(String key) {
    return (boolean) values.getOrDefault(key, Boolean.FALSE);
  }

  public Integer optValueAsInteger(String key, int defaultValue) {
    return (Integer) values.getOrDefault(key, defaultValue);
  }

  public Long optValueAsLong(String key, long defaultValue) {
    return (Long) values.getOrDefault(key, defaultValue);
  }

  public Object getValue(String key) {
    return values.get(key);
  }

  public Map<String, Object> getValueAsMap(String key) {
    return (Map<String, Object>) values.get(key);
  }

  public Map<String, Object> optValueAsMap(String key, Map<String, Object> defaultValue) {
    return (Map<String, Object>) values.getOrDefault(key, defaultValue);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public String extractTrustFramework() {
    return optValueAsString("trust_framework", "");
  }

  public String extractEvidenceDocumentType() {
    return optValueAsString("evidence_document_type", "");
  }

  public Object extractEvidenceDocumentDetail() {
    return getValue("evidence_document_details");
  }

  public String extractEvidenceMethod() {
    return optValueAsString("evidence_method", "");
  }

  public UserIdentifier userIdentifier() {
    return new UserIdentifier(optValueAsString("user_id", ""));
  }
}
