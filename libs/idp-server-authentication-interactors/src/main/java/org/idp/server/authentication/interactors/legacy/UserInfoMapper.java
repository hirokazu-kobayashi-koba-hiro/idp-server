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

package org.idp.server.authentication.interactors.legacy;

import java.util.*;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.address.Address;
import org.idp.server.platform.json.JsonNodeWrapper;

public class UserInfoMapper {
  String providerName;
  JsonNodeWrapper body;
  List<UserInfoMappingRule> mappingRules;

  public UserInfoMapper(
      String providerName, JsonNodeWrapper body, List<UserInfoMappingRule> mappingRules) {
    this.providerName = providerName;
    this.body = body;
    this.mappingRules = mappingRules;
  }

  public User toUser() {
    User user = new User();
    user.setProviderId(providerName);

    for (UserInfoMappingRule rule : mappingRules) {
      String fromPath = rule.getFrom().replace(".", "/");
      Object mappedValue;

      switch (rule.getType()) {
        case "list.string" -> {
          List<JsonNodeWrapper> listNodes = body.getValueAsJsonNodeList(fromPath);
          List<String> values = new ArrayList<>();
          for (JsonNodeWrapper node : listNodes) {
            values.add(node.asText());
          }
          mappedValue = values;
        }
        case "map" -> {
          JsonNodeWrapper mapNode = body.getValueAsJsonNode(fromPath);
          Map<String, Object> map = new HashMap<>();
          Iterator<String> fieldNames = mapNode.fieldNames();
          while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            map.put(key, mapNode.getValueOrEmptyAsString(key));
          }
          mappedValue = map;
        }
        case "address" -> {
          JsonNodeWrapper addrNode = body.getValueAsJsonNode(fromPath);
          Address address = new Address();
          address.setFormatted(addrNode.getValueOrEmptyAsString("formatted"));
          address.setStreetAddress(addrNode.getValueOrEmptyAsString("street_address"));
          address.setLocality(addrNode.getValueOrEmptyAsString("locality"));
          address.setRegion(addrNode.getValueOrEmptyAsString("region"));
          address.setPostalCode(addrNode.getValueOrEmptyAsString("postal_code"));
          address.setCountry(addrNode.getValueOrEmptyAsString("country"));
          mappedValue = address;
        }
        case "int" -> {
          String value = body.getValueOrEmptyAsString(fromPath);
          try {
            mappedValue = Integer.parseInt(value);
          } catch (NumberFormatException e) {
            mappedValue = null;
          }
        }
        case "boolean" -> {
          String value = body.getValueOrEmptyAsString(fromPath);
          mappedValue = Boolean.parseBoolean(value);
        }
        case "string" -> mappedValue = body.getValueOrEmptyAsString(fromPath);
        default -> mappedValue = null;
      }

      if (mappedValue == null) continue;

      switch (rule.getTo()) {
        case "provider_user_id" -> user.setProviderUserId(mappedValue.toString());
        case "name" -> user.setName(mappedValue.toString());
        case "given_name" -> user.setGivenName(mappedValue.toString());
        case "family_name" -> user.setFamilyName(mappedValue.toString());
        case "middle_name" -> user.setMiddleName(mappedValue.toString());
        case "nickname" -> user.setNickname(mappedValue.toString());
        case "preferred_username" -> user.setPreferredUsername(mappedValue.toString());
        case "profile" -> user.setProfile(mappedValue.toString());
        case "picture" -> user.setPicture(mappedValue.toString());
        case "website" -> user.setWebsite(mappedValue.toString());
        case "email" -> user.setEmail(mappedValue.toString());
        case "email_verified" -> user.setEmailVerified((Boolean) mappedValue);
        case "gender" -> user.setGender(mappedValue.toString());
        case "birthdate" -> user.setBirthdate(mappedValue.toString());
        case "zoneinfo" -> user.setZoneinfo(mappedValue.toString());
        case "phone_number" -> user.setPhoneNumber(mappedValue.toString());
        case "phone_number_verified" -> user.setPhoneNumberVerified((Boolean) mappedValue);
        case "address" -> user.setAddress((Address) mappedValue);
          //        case "roles" -> user.setRoles((List<String>) mappedValue);
        case "permissions" -> user.setPermissions((List<String>) mappedValue);
        default -> user.customPropertiesValue().put(rule.getTo(), mappedValue);
      }
    }

    return user;
  }
}
