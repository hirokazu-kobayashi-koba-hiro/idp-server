package org.idp.server.core.authentication.legacy;

import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.oauth.identity.Address;
import org.idp.server.core.oauth.identity.User;

import java.util.*;

public class UserInfoMapper {
    JsonNodeWrapper body;
    List<UserInfoMappingRule> mappingRules;

    public UserInfoMapper(JsonNodeWrapper body, List<UserInfoMappingRule> mappingRules) {
        this.body = body;
        this.mappingRules = mappingRules;
    }

    public User toUser() {
        User user = new User();

        for (UserInfoMappingRule rule : mappingRules) {
            String fromPath = rule.getFrom().replace(".", "/");
            Object mappedValue;

            switch (rule.getType()) {
                case "list" -> {
                    List<JsonNodeWrapper> listNodes = body.getValueAsJsonNodeList(fromPath);
                    List<String> values = new ArrayList<>();
                    for (JsonNodeWrapper node : listNodes) {
                        values.add(node.getValueOrEmptyAsString(""));
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
                default -> {
                    String value = body.getValueOrEmptyAsString(fromPath);
                    mappedValue = value == null || value.isEmpty() ? null : value;
                }
            }

            if (mappedValue == null) continue;

            switch (rule.getTo()) {
                case "email" -> user.setEmail(mappedValue.toString());
                case "name" -> user.setName(mappedValue.toString());
                case "given_name" -> user.setGivenName(mappedValue.toString());
                case "family_name" -> user.setFamilyName(mappedValue.toString());
                case "preferred_username" -> user.setPreferredUsername(mappedValue.toString());
                case "phone_number" -> user.setPhoneNumber(mappedValue.toString());
                case "birthdate" -> user.setBirthdate(mappedValue.toString());
                case "gender" -> user.setGender(mappedValue.toString());
                case "email_verified" -> user.setEmailVerified((Boolean) mappedValue);
                case "phone_number_verified" -> user.setPhoneNumberVerified((Boolean) mappedValue);
                case "address" -> user.setAddress((Address) mappedValue);
                default -> user.customPropertiesValue().put(rule.getTo(), mappedValue);
            }
        }

        return user;
    }
}
