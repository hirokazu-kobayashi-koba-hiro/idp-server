package org.idp.server.core.oidc.identity;

public class UserUpdater {
  User user;
  User existingUser;

  public UserUpdater(User user, User existingUser) {
    this.user = user;
    this.existingUser = existingUser;
  }

  public User update() {
    User updatedUser = existingUser;
    if (user.hasEmail()) updatedUser.setEmail(user.email());
    if (user.hasProviderOriginalPayload())
      updatedUser.setProviderOriginalPayload(user.providerOriginalPayload());
    if (user.hasName()) updatedUser.setName(user.name());
    if (user.hasGivenName()) updatedUser.setGivenName(user.givenName());
    if (user.hasFamilyName()) updatedUser.setFamilyName(user.familyName());
    if (user.hasMiddleName()) updatedUser.setMiddleName(user.middleName());
    if (user.hasNickname()) updatedUser.setNickname(user.nickname());
    if (user.hasPreferredUsername()) updatedUser.setPreferredUsername(user.preferredUsername());
    if (user.hasProfile()) updatedUser.setProfile(user.profile());
    if (user.hasPicture()) updatedUser.setPicture(user.picture());
    if (user.hasWebsite()) updatedUser.setWebsite(user.website());
    if (user.hasEmail()) updatedUser.setEmail(user.email());
    if (user.hasEmailVerified()) updatedUser.setEmailVerified(user.emailVerified());
    if (user.hasGender()) updatedUser.setGender(user.gender());
    if (user.hasBirthdate()) updatedUser.setBirthdate(user.birthdate());
    if (user.hasZoneinfo()) updatedUser.setZoneinfo(user.zoneinfo());
    if (user.hasLocale()) updatedUser.setLocale(user.locale());
    if (user.hasPhoneNumber()) updatedUser.setPhoneNumber(user.phoneNumber());
    if (user.hasPhoneNumberVerified())
      updatedUser.setPhoneNumberVerified(user.phoneNumberVerified());
    if (user.hasUpdatedAt()) updatedUser.setUpdatedAt(user.updatedAt());
    if (user.hasAddress()) updatedUser.setAddress(user.address());
    if (user.hasCustomProperties()) updatedUser.setCustomProperties(user.customPropertiesValue());
    if (user.hasMultiFactorAuthentication())
      updatedUser.setMultiFactorAuthentication(user.multiFactorAuthentication());
    if (user.hasRoles()) updatedUser.setRoles(user.roles());
    if (user.hasAssignedTenants()) updatedUser.setAssignedTenants(user.assignedTenants());

    return updatedUser;
  }
}
