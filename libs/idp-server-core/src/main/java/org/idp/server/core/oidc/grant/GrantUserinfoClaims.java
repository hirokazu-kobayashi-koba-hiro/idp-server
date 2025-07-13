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

package org.idp.server.core.oidc.grant;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.oidc.id_token.RequestedUserinfoClaims;
import org.idp.server.core.oidc.type.oauth.Scopes;

public class GrantUserinfoClaims implements Iterable<String> {

  Set<String> values;
  private static final String delimiter = " ";

  public GrantUserinfoClaims() {
    this.values = new HashSet<>();
  }

  public static GrantUserinfoClaims create(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    Set<String> claims = new HashSet<>();
    if (shouldAddName(scopes, supportedClaims, userinfoClaims)) claims.add("name");
    if (shouldAddGivenName(scopes, supportedClaims, userinfoClaims)) claims.add("given_name");
    if (shouldAddFamilyName(scopes, supportedClaims, userinfoClaims)) claims.add("family_name");
    if (shouldAddMiddleName(scopes, supportedClaims, userinfoClaims)) claims.add("middle_name");
    if (shouldAddNickname(scopes, supportedClaims, userinfoClaims)) claims.add("nickname");
    if (shouldAddPreferredUsername(scopes, supportedClaims, userinfoClaims))
      claims.add("preferred_username");
    if (shouldAddProfile(scopes, supportedClaims, userinfoClaims)) claims.add("profile");
    if (shouldAddPicture(scopes, supportedClaims, userinfoClaims)) claims.add("picture");
    if (shouldAddWebsite(scopes, supportedClaims, userinfoClaims)) claims.add("website");
    if (shouldAddEmail(scopes, supportedClaims, userinfoClaims)) claims.add("email");
    if (shouldAddEmailVerified(scopes, supportedClaims, userinfoClaims))
      claims.add("email_verified");
    if (shouldAddGender(scopes, supportedClaims, userinfoClaims)) claims.add("gender");
    if (shouldAddBirthdate(scopes, supportedClaims, userinfoClaims)) claims.add("birthdate");
    if (shouldAddZoneinfo(scopes, supportedClaims, userinfoClaims)) claims.add("zoneinfo");
    if (shouldAddLocale(scopes, supportedClaims, userinfoClaims)) claims.add("locale");
    if (shouldAddPhoneNumber(scopes, supportedClaims, userinfoClaims)) claims.add("phone_number");
    if (shouldAddPhoneNumberVerified(scopes, supportedClaims, userinfoClaims))
      claims.add("phone_number_verified");
    if (shouldAddAddress(scopes, supportedClaims, userinfoClaims)) claims.add("address");
    if (shouldAddUpdatedAt(scopes, supportedClaims, userinfoClaims)) claims.add("updated_at");

    return new GrantUserinfoClaims(claims);
  }

  public GrantUserinfoClaims(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(delimiter)).collect(Collectors.toSet());
  }

  public GrantUserinfoClaims(Set<String> values) {
    this.values = values;
  }

  public GrantUserinfoClaims merge(GrantUserinfoClaims other) {
    Set<String> newValues = new HashSet<>(this.values);
    newValues.addAll(other.values);
    return new GrantUserinfoClaims(newValues);
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Set<String> toStringSet() {
    return values;
  }

  public String toStringValues() {
    return String.join(delimiter, values);
  }

  public boolean hasName() {
    return Objects.nonNull(values) && values.contains("name");
  }

  public boolean hasGivenName() {
    return Objects.nonNull(values) && values.contains("given_name");
  }

  public boolean hasFamilyName() {
    return Objects.nonNull(values) && values.contains("family_name");
  }

  public boolean hasMiddleName() {
    return Objects.nonNull(values) && values.contains("middle_name");
  }

  public boolean hasNickname() {
    return Objects.nonNull(values) && values.contains("nickname");
  }

  public boolean hasPreferredUsername() {
    return Objects.nonNull(values) && values.contains("preferred_username");
  }

  public boolean hasProfile() {
    return Objects.nonNull(values) && values.contains("profile");
  }

  public boolean hasPicture() {
    return Objects.nonNull(values) && values.contains("picture");
  }

  public boolean hasWebsite() {
    return Objects.nonNull(values) && values.contains("website");
  }

  public boolean hasEmail() {
    return Objects.nonNull(values) && values.contains("email");
  }

  public boolean hasEmailVerified() {
    return Objects.nonNull(values) && values.contains("email_verified");
  }

  public boolean hasGender() {
    return Objects.nonNull(values) && values.contains("gender");
  }

  public boolean hasBirthdate() {
    return Objects.nonNull(values) && values.contains("birthdate");
  }

  public boolean hasZoneinfo() {
    return Objects.nonNull(values) && values.contains("zoneinfo");
  }

  public boolean hasLocale() {
    return Objects.nonNull(values) && values.contains("locale");
  }

  public boolean hasPhoneNumber() {
    return Objects.nonNull(values) && values.contains("phone_number");
  }

  public boolean hasPhoneNumberVerified() {
    return Objects.nonNull(values) && values.contains("phone_number_verified");
  }

  public boolean hasAddress() {
    return Objects.nonNull(values) && values.contains("address");
  }

  public boolean hasUpdatedAt() {
    return Objects.nonNull(values) && values.contains("updated_at");
  }

  private static boolean shouldAddName(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasName();
  }

  private static boolean shouldAddGivenName(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("given_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasGivenName();
  }

  private static boolean shouldAddFamilyName(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("family_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasMiddleName();
  }

  private static boolean shouldAddMiddleName(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("middle_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasMiddleName();
  }

  private static boolean shouldAddNickname(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("nickname")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasNickname();
  }

  private static boolean shouldAddPreferredUsername(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("preferred_username")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasPreferredUsername();
  }

  private static boolean shouldAddProfile(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("profile")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasProfile();
  }

  private static boolean shouldAddPicture(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("picture")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasPicture();
  }

  private static boolean shouldAddWebsite(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("website")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasWebsite();
  }

  private static boolean shouldAddEmail(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("email")) {
      return false;
    }
    return scopes.contains("email") || userinfoClaims.hasEmail();
  }

  private static boolean shouldAddEmailVerified(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("email_verified")) {
      return false;
    }
    return scopes.contains("email") || userinfoClaims.hasEmailVerified();
  }

  private static boolean shouldAddGender(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("gender")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasGender();
  }

  private static boolean shouldAddBirthdate(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("birthdate")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasBirthdate();
  }

  private static boolean shouldAddZoneinfo(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("zoneinfo")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasZoneinfo();
  }

  private static boolean shouldAddLocale(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("locale")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasLocale();
  }

  private static boolean shouldAddPhoneNumber(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("phone_number")) {
      return false;
    }
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumber();
  }

  private static boolean shouldAddPhoneNumberVerified(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("phone_number_verified")) {
      return false;
    }
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumberVerified();
  }

  private static boolean shouldAddAddress(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("address")) {
      return false;
    }
    return scopes.contains("address") || userinfoClaims.hasAddress();
  }

  private static boolean shouldAddUpdatedAt(
      Scopes scopes, List<String> supportedClaims, RequestedUserinfoClaims userinfoClaims) {
    if (!supportedClaims.contains("updated_at")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasUpdatedAt();
  }

  public boolean contains(String claims) {
    return values.contains(claims);
  }
}
