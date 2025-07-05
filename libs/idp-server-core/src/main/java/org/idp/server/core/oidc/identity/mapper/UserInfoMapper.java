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

package org.idp.server.core.oidc.identity.mapper;

import java.util.*;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.address.Address;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class UserInfoMapper {
  String providerName;
  Map<String, List<String>> headers;
  List<UserinfoMappingRule> mappingRules;
  JsonPathWrapper bodyJsonPath;

  public UserInfoMapper(
      String providerName,
      Map<String, List<String>> headers,
      JsonNodeWrapper body,
      List<UserinfoMappingRule> mappingRules) {
    this.providerName = providerName;
    this.headers = headers;
    this.mappingRules = mappingRules;
    this.bodyJsonPath = new JsonPathWrapper(body.toJson());
  }

  public User toUser() {
    User user = new User();
    user.setProviderId(providerName);

    for (UserinfoMappingRule rule : mappingRules) {
      Object mappedValue = null;

      switch (rule.getSource()) {
        case "header" -> mappedValue = extractFromHeader(rule);
        case "body" -> mappedValue = extractFromBody(rule);
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
        case "permissions" -> user.setPermissions((List<String>) mappedValue);
        case "custom_properties" -> user.addCustomProperties((HashMap<String, Object>) mappedValue);
        default -> user.customPropertiesValue().put(rule.getTo(), mappedValue);
      }
    }

    return user;
  }

  private Object extractFromHeader(UserinfoMappingRule rule) {
    String from = rule.getFrom().replace("$.", "");
    List<String> rawValues = headers.get(from);
    if (rawValues == null) return null;

    String rawValue = rawValues.getFirst();
    return switch (rule.getType()) {
      case "string" -> rawValue;
      case "boolean" -> Boolean.parseBoolean(rawValue);
      case "int" -> {
        try {
          yield Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
          yield null;
        }
      }
      default -> null;
    };
  }

  private Object extractFromBody(UserinfoMappingRule rule) {

    if (rule.hasItemIndex()) {
      int index = rule.getItemIndexOrDefault(0);

      return switch (rule.getType()) {
        case "list<string>" -> {
          List<String> strings = bodyJsonPath.readAsStringList(rule.getFrom());
          if (strings.size() > index) {
            yield strings.get(index);
          }
          yield null;
        }
        case "list<object>" -> {
          List<Map<String, Object>> mapList = bodyJsonPath.readAsMapList(rule.getFrom());
          String field = rule.getFieldOrDefault("");
          if (mapList.size() > index) {
            Map<String, Object> objectMap = mapList.get(index);
            yield objectMap.get(field);
          }
          yield null;
        }
        default -> null;
      };
    }

    return switch (rule.getType()) {
      case "string" -> bodyJsonPath.readAsString(rule.getFrom());
      case "boolean" -> bodyJsonPath.readAsBoolean(rule.getFrom());
      case "int" -> bodyJsonPath.readAsInt(rule.getFrom());
      case "list<string>" -> bodyJsonPath.readAsStringList(rule.getFrom());
      case "object" -> bodyJsonPath.readAsMap(rule.getFrom());
      case "list<object>" -> bodyJsonPath.readAsMapList(rule.getFrom());
      case "address" -> {
        Map<String, String> stringObjectMap = bodyJsonPath.readAsStringMap(rule.getFrom());
        Address address = new Address();
        address.setFormatted(stringObjectMap.get("formatted"));
        address.setStreetAddress(stringObjectMap.get("street_address"));
        address.setLocality(stringObjectMap.get("locality"));
        address.setRegion(stringObjectMap.get("region"));
        address.setPostalCode(stringObjectMap.get("postal_code"));
        address.setCountry(stringObjectMap.get("country"));
        yield address;
      }
      default -> null;
    };
  }
}
