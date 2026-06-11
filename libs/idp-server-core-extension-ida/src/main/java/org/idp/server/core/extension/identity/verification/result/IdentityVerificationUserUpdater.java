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

package org.idp.server.core.extension.identity.verification.result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.configuration.verified_claims.IdentityVerificationResultConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationUserUpdater {

  public static User update(
      User user,
      IdentityVerificationContext context,
      IdentityVerificationResultConfig resultConfig) {

    User updated = user;

    if (resultConfig.hasUserClaimsMappingRules()) {
      Map<String, Object> mapped = execute(context, resultConfig.userClaimsMappingRules());
      // status and custom_properties have dedicated configurations
      // (user_status, custom_properties_mapping_rules); status must go through
      // UserLifecycleManager and custom properties are merged, not replaced
      mapped.remove("status");
      mapped.remove("custom_properties");
      User patchUser = JsonConverter.snakeCaseInstance().read(mapped, User.class);
      updated = updated.updateWith(patchUser);
    }

    if (resultConfig.hasCustomPropertiesMappingRules()) {
      Map<String, Object> mapped = execute(context, resultConfig.customPropertiesMappingRules());
      updated = updated.addCustomProperties(new HashMap<>(mapped));
    }

    if (resultConfig.requiresUserStatusTransition()) {
      UserStatus newStatus = resultConfig.userStatus();
      if (updated.status() != newStatus) {
        updated = updated.transitStatus(newStatus);
      }
    }

    return updated;
  }

  static Map<String, Object> execute(
      IdentityVerificationContext context, List<MappingRule> mappingRules) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(context.toMap());
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(mappingRules, jsonPath);
  }
}
