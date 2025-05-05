package org.idp.server.core.adapters.datasource.identity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.address.Address;
import org.idp.server.core.identity.device.AuthenticationDevice;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

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
    user.setPhoneNumberVerified(
        Boolean.parseBoolean(stringMap.getOrDefault("phone_number_verified", "false")));
    user.setHashedPassword(stringMap.getOrDefault("hashed_password", ""));

    if (stringMap.containsKey("updated_at")) {
      user.setUpdatedAt(parseDateTime(stringMap.get("updated_at")));
    }

    if (stringMap.containsKey("address") && !stringMap.get("address").isEmpty()) {
      Address address = jsonConverter.read(stringMap.get("address"), Address.class);
      user.setAddress(address);
    }

    if (stringMap.containsKey("multi_factor_authentication")
        && !stringMap.get("multi_factor_authentication").isEmpty()) {
      HashMap<String, Object> customProps =
          jsonConverter.read(stringMap.get("multi_factor_authentication"), HashMap.class);
      user.setMultiFactorAuthentication(customProps);
    }

    if (stringMap.containsKey("custom_properties")
        && !stringMap.get("custom_properties").isEmpty()) {
      HashMap<String, Object> customProps =
          jsonConverter.read(stringMap.get("custom_properties"), HashMap.class);
      user.setCustomProperties(customProps);
    }

    if (stringMap.containsKey("credentials") && !stringMap.get("credentials").isEmpty()) {
      List<HashMap<String, Object>> credentials =
          jsonConverter.read(stringMap.get("credentials"), List.class);
      user.setCredentials(credentials);
    }
    if (stringMap.containsKey("roles") && !stringMap.get("roles").equals("[]")) {
      List<String> roles = jsonConverter.read(stringMap.get("roles"), List.class);
      List<String> filtered = roles.stream().filter(Objects::nonNull).toList();
      user.setRoles(filtered);
    }

    if (stringMap.containsKey("permissions") && !stringMap.get("permissions").equals("[]")) {
      List<String> permissions = jsonConverter.read(stringMap.get("permissions"), List.class);
      List<String> filtered = permissions.stream().filter(Objects::nonNull).toList();
      user.setPermissions(filtered);
    }
    if (stringMap.containsKey("authentication_devices")
        && stringMap.get("authentication_devices") != null
        && !stringMap.get("authentication_devices").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper =
          jsonConverter.readTree(stringMap.get("authentication_devices"));
      List<JsonNodeWrapper> wrapperList = jsonNodeWrapper.elements();
      List<AuthenticationDevice> authenticationDevices = new ArrayList<>();
      for (JsonNodeWrapper wrapper : wrapperList) {
        String id = wrapper.getValueOrEmptyAsString("id");
        String platform = wrapper.getValueOrEmptyAsString("platform");
        String os = wrapper.getValueOrEmptyAsString("os");
        String model = wrapper.getValueOrEmptyAsString("model");
        String notificationChannel = wrapper.getValueOrEmptyAsString("notification_channel");
        String notificationToken = wrapper.getValueOrEmptyAsString("notification_token");
        boolean preferredForNotification = wrapper.getValueAsBoolean("preferred_for_notification");
        AuthenticationDevice authenticationDevice =
            new AuthenticationDevice(
                id,
                platform,
                os,
                model,
                notificationChannel,
                notificationToken,
                preferredForNotification);
        authenticationDevices.add(authenticationDevice);
      }
      user.setAuthenticationDevices(authenticationDevices);
    }

    if (stringMap.containsKey("verified_claims") && !stringMap.get("verified_claims").isEmpty()) {
      HashMap<String, Object> verifiedClaims =
          jsonConverter.read(stringMap.get("verified_claims"), HashMap.class);
      user.setVerifiedClaims(verifiedClaims);
    }

    UserStatus userStatus = UserStatus.of(stringMap.getOrDefault("status", ""));
    user.setStatus(userStatus);

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
