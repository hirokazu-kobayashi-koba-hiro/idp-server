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

package org.idp.server.core.openid.identity.mapper;

import java.util.*;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class UserInfoMapper {

  JsonConverter jsonConverter;
  List<MappingRule> mappingRules;
  String providerName;
  JsonPathWrapper jsonPath;

  public UserInfoMapper(
      List<MappingRule> mappingRules, Map<String, Object> contents, String providerName) {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
    this.providerName = providerName;
    this.mappingRules = mappingRules;
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(contents);
    this.jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
  }

  public User toUser() {
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPath);
    User user = jsonConverter.read(executed, User.class);
    user.setProviderId(providerName);

    return user;
  }
}
