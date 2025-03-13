package org.idp.server.core.handler.user.datasource;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.Address;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.AuthorizationCode;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.Scopes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static User convert(Map<String, String> stringMap) {
    User user = new User();

    user.setSub(stringMap.getOrDefault("id", ""));
    user.setProviderId(stringMap.getOrDefault("provider_id", ""));
    user.setProviderUserId(stringMap.getOrDefault("provider_user_id", ""));
    user.setName(stringMap.getOrDefault("name", ""));
    user.setGivenName(stringMap.getOrDefault("given_name", ""));
    user.setFamilyName(stringMap.getOrDefault("family_name", ""));
    user.setMiddleName(stringMap.getOrDefault("middle_name", ""));
    user.setNickname(stringMap.getOrDefault("nickname", ""));
    user.setPreferredUsername(stringMap.getOrDefault("preferred_username", ""));
    user.setProfile(stringMap.getOrDefault("profile", ""));
    user.setPicture(stringMap.getOrDefault("picture", ""));
    user.setWebsite(stringMap.getOrDefault("website", ""));
    user.setEmail(stringMap.getOrDefault("email", ""));
    user.setEmailVerified(Boolean.parseBoolean(stringMap.getOrDefault("email_verified", "false")));
    user.setGender(stringMap.getOrDefault("gender", ""));
    user.setBirthdate(stringMap.getOrDefault("birthdate", ""));
    user.setZoneinfo(stringMap.getOrDefault("zoneinfo", ""));
    user.setLocale(stringMap.getOrDefault("locale", ""));
    user.setPhoneNumber(stringMap.getOrDefault("phone_number", ""));
    user.setPhoneNumberVerified(Boolean.parseBoolean(stringMap.getOrDefault("phone_number_verified", "false")));
    user.setHashedPassword(stringMap.getOrDefault("hashed_password", ""));

    if (stringMap.containsKey("updated_at")) {
      user.setUpdatedAt(parseDateTime(stringMap.get("updated_at")));
    }

    if (stringMap.containsKey("address") && !stringMap.get("address").isEmpty()) {
      Address address = jsonConverter.read(stringMap.get("address"), Address.class);
      user.setAddress(address);
    }

    if (stringMap.containsKey("custom_properties") && !stringMap.get("custom_properties").isEmpty()) {
      HashMap<String, Object> customProps = jsonConverter.read(stringMap.get("custom_properties"), HashMap.class);
      user.setCustomProperties(customProps);
    }

    if (stringMap.containsKey("credentials") && !stringMap.get("credentials").isEmpty()) {
      List<HashMap<String, Object>> credentials = jsonConverter.read(stringMap.get("credentials"), List.class);
      user.setCredentials(credentials);
    }

    return user;
  }

  static LocalDateTime parseDateTime(String dateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    return LocalDateTime.parse(normalizeDateTime(dateTime), formatter);
  }

  private static String normalizeDateTime(String dateTimeStr) {
    if (!dateTimeStr.contains(".")) {
      return dateTimeStr + ".000000"; // No fraction present, add six zeroes
    }

    String[] parts = dateTimeStr.split("\\.");
    String fraction = parts[1];

    // Ensure fraction has exactly 6 digits
    if (fraction.length() < 6) {
      fraction = String.format("%-6s", fraction).replace(' ', '0'); // Pad with zeros
    } else if (fraction.length() > 6) {
      fraction = fraction.substring(0, 6); // Trim to 6 digits
    }

    return parts[0] + "." + fraction;
  }


}
