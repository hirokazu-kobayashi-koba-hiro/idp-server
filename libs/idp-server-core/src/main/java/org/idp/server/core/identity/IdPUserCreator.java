package org.idp.server.core.identity;

import java.util.List;
import java.util.UUID;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;

public class IdPUserCreator {

  JsonSchemaDefinition definition;
  AuthenticationInteractionRequest request;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public IdPUserCreator(
      JsonSchemaDefinition definition,
      AuthenticationInteractionRequest request,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.definition = definition;
    this.request = request;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public User create() {
    User user = new User();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setProviderId("idp-server");
    user.setProviderUserId(id);
    if (definition.hasProperty("name") && request.containsKey("name")) {
      user.setName(request.getValueAsString("name"));
    }

    if (definition.hasProperty("given_name") && request.containsKey("given_name")) {
      user.setGivenName(request.getValueAsString("given_name"));
    }

    if (definition.hasProperty("family_name") && request.containsKey("family_name")) {
      user.setFamilyName(request.getValueAsString("family_name"));
    }

    if (definition.hasProperty("middle_name") && request.containsKey("middle_name")) {
      user.setMiddleName(request.getValueAsString("middle_name"));
    }

    if (definition.hasProperty("nickname") && request.containsKey("nickname")) {
      user.setNickname(request.getValueAsString("nickname"));
    }

    if (definition.hasProperty("preferred_username") && request.containsKey("preferred_username")) {
      user.setPreferredUsername(request.getValueAsString("preferred_username"));
    }

    if (definition.hasProperty("profile") && request.containsKey("profile")) {
      user.setProfile(request.getValueAsString("profile"));
    }

    if (definition.hasProperty("picture") && request.containsKey("picture")) {
      user.setPicture(request.getValueAsString("picture"));
    }

    if (definition.hasProperty("website") && request.containsKey("website")) {
      user.setWebsite(request.getValueAsString("website"));
    }

    if (definition.hasProperty("email") && request.containsKey("email")) {
      user.setEmail(request.getValueAsString("email"));
    }

    if (definition.hasProperty("email_verified") && request.containsKey("email_verified")) {
      user.setEmailVerified(request.getValueAsBoolean("email_verified"));
    }

    if (definition.hasProperty("gender") && request.containsKey("gender")) {
      user.setGender(request.getValueAsString("gender"));
    }

    if (definition.hasProperty("birthdate") && request.containsKey("birthdate")) {
      user.setBirthdate(request.getValueAsString("birthdate"));
    }

    if (definition.hasProperty("zoneinfo") && request.containsKey("zoneinfo")) {
      user.setZoneinfo(request.getValueAsString("zoneinfo"));
    }

    if (definition.hasProperty("locale") && request.containsKey("locale")) {
      user.setLocale(request.getValueAsString("locale"));
    }

    if (definition.hasProperty("phone_number") && request.containsKey("phone_number")) {
      user.setPhoneNumber(request.getValueAsString("phone_number"));
    }

    if (definition.hasProperty("phone_number_verified")
        && request.containsKey("phone_number_verified")) {
      user.setPhoneNumberVerified(request.getValueAsBoolean("phone_number_verified"));
    }

    if (definition.hasProperty("password") && request.containsKey("password")) {
      String password = request.getValueAsString("password");
      String hashedPassword = passwordEncodeDelegation.encode(password);
      user.setHashedPassword(hashedPassword);
    }

    // TODO multi role
    if (definition.hasProperty("roles") && request.containsKey("role_id")) {
      String roleId = request.getValueAsString("role_id");
      String toleName = request.getValueAsString("role_name");
      List<UserRole> roles = List.of(new UserRole(roleId, toleName));
      user.setRoles(roles);
    }

    if (definition.hasProperty("assigned_tenants") && request.containsKey("tenant_id")) {
      String tenantId = request.getValueAsString("tenant_id");
      List<String> assignedTenants = List.of(tenantId);
      user.setAssignedTenants(assignedTenants);
    }

    user.transitStatus(UserStatus.REGISTERED);

    return user;
  }
}
