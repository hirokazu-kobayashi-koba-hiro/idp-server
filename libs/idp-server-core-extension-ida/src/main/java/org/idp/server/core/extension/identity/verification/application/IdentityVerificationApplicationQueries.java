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
import java.util.UUID;
import org.idp.server.platform.uuid.UuidConvertable;

public class IdentityVerificationApplicationQueries implements UuidConvertable {

  Map<String, String> values;

  public IdentityVerificationApplicationQueries() {}

  public IdentityVerificationApplicationQueries(Map<String, String> values) {
    this.values = values;
  }

  public boolean hasId() {
    return values.containsKey("id");
  }

  public String id() {
    return values.get("id");
  }

  public UUID idAsUuid() {
    return convertUuid(id());
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public String type() {
    return values.get("type");
  }

  public boolean hasClientId() {
    return values.containsKey("client_id");
  }

  public String clientId() {
    return values.get("client_id");
  }

  public boolean hasExternalApplicationId() {
    return values.containsKey("external_application_id");
  }

  public String externalApplicationId() {
    return values.get("external_application_id");
  }

  public boolean hasExternalService() {
    return values.containsKey("external_service");
  }

  public String externalService() {
    return values.get("external_service");
  }

  public boolean hasTrustFramework() {
    return values.containsKey("trust_framework");
  }

  public String trustFramework() {
    return values.get("trust_framework");
  }

  public boolean hasStatus() {
    return values.containsKey("status");
  }

  public String status() {
    return values.get("status");
  }
}
