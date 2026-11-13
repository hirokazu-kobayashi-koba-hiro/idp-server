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
package org.idp.server.core.adapters.datasource.crypto.datakey;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.crypto.datakey.DataKeyStatus;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static TenantDataKey convert(Map<String, String> row) {
    if (row == null || row.isEmpty()) {
      return new TenantDataKey();
    }
    return new TenantDataKey(
        row.get("id"),
        new TenantIdentifier(row.get("tenant_id")),
        row.get("purpose"),
        Integer.parseInt(row.get("version")),
        DataKeyStatus.of(row.get("status")),
        jsonConverter.read(row.get("wrapped_key"), EncryptedData.class),
        row.get("kek_id"),
        row.get("created_at") == null
            ? null
            : LocalDateTime.parse(row.get("created_at").replace(" ", "T")));
  }

  static String toJson(EncryptedData data) {
    return jsonConverter.write(data);
  }
}
