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
    if (user.hasExternalProviderOriginalPayload())
      updatedUser.setExternalProviderOriginalPayload(user.externalProviderOriginalPayload());
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
    if (user.hasAuthenticationDevices())
      updatedUser.setAuthenticationDevices(user.authenticationDevicesAsList());
    if (user.hasRoles()) updatedUser.setRoles(user.roles());
    if (user.hasAssignedTenants()) updatedUser.setAssignedTenants(user.assignedTenants());

    return updatedUser;
  }
}
