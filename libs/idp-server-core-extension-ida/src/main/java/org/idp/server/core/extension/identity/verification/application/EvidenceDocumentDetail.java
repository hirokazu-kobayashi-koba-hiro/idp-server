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

package org.idp.server.core.extension.identity.verification.application;

import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

public class EvidenceDocumentDetail {

  JsonNodeWrapper json;

  public EvidenceDocumentDetail() {
    this.json = JsonNodeWrapper.empty();
  }

  public static EvidenceDocumentDetail fromString(String json) {
    return new EvidenceDocumentDetail(JsonNodeWrapper.fromString(json));
  }

  public static EvidenceDocumentDetail fromObject(Object object) {
    return new EvidenceDocumentDetail(JsonNodeWrapper.fromObject(object));
  }

  public EvidenceDocumentDetail(JsonNodeWrapper json) {
    this.json = json;
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }

  public String toJson() {
    return json.toJson();
  }
}
