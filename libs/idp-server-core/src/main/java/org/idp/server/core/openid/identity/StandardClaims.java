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

package org.idp.server.core.openid.identity;

import java.util.Optional;

/**
 * Resolves a single OIDC standard claim value from a {@link User} by claim name.
 *
 * <p>Centralizes the claim-name → user-field mapping for the OIDC Core §5.1 standard claims. Each
 * claim is guarded by the corresponding {@code User#hasXxx()} so a value is only returned when it
 * is actually present. Unknown names and absent values both yield {@link Optional#empty()} — the
 * claim must then be omitted (never emitted as {@code null}), consistent with OIDC Core §5.3.2.
 *
 * <p>Used by the {@code standard_claims:} scope mapping to surface standard claims where they are
 * not otherwise available (e.g. the access token).
 */
public final class StandardClaims {

  private StandardClaims() {}

  public static Optional<Object> resolve(User user, String claimName) {
    return switch (claimName) {
      case "name" -> user.hasName() ? Optional.of(user.name()) : Optional.empty();
      case "given_name" -> user.hasGivenName() ? Optional.of(user.givenName()) : Optional.empty();
      case "family_name" ->
          user.hasFamilyName() ? Optional.of(user.familyName()) : Optional.empty();
      case "middle_name" ->
          user.hasMiddleName() ? Optional.of(user.middleName()) : Optional.empty();
      case "nickname" -> user.hasNickname() ? Optional.of(user.nickname()) : Optional.empty();
      case "preferred_username" ->
          user.hasPreferredUsername() ? Optional.of(user.preferredUsername()) : Optional.empty();
      case "profile" -> user.hasProfile() ? Optional.of(user.profile()) : Optional.empty();
      case "picture" -> user.hasPicture() ? Optional.of(user.picture()) : Optional.empty();
      case "website" -> user.hasWebsite() ? Optional.of(user.website()) : Optional.empty();
      case "email" -> user.hasEmail() ? Optional.of(user.email()) : Optional.empty();
      case "email_verified" ->
          user.hasEmailVerified() ? Optional.of(user.emailVerified()) : Optional.empty();
      case "gender" -> user.hasGender() ? Optional.of(user.gender()) : Optional.empty();
      case "birthdate" -> user.hasBirthdate() ? Optional.of(user.birthdate()) : Optional.empty();
      case "zoneinfo" -> user.hasZoneinfo() ? Optional.of(user.zoneinfo()) : Optional.empty();
      case "locale" -> user.hasLocale() ? Optional.of(user.locale()) : Optional.empty();
      case "phone_number" ->
          user.hasPhoneNumber() ? Optional.of(user.phoneNumber()) : Optional.empty();
      case "phone_number_verified" ->
          user.hasPhoneNumberVerified()
              ? Optional.of(user.phoneNumberVerified())
              : Optional.empty();
      case "address" -> user.hasAddress() ? Optional.of(user.address().toMap()) : Optional.empty();
      case "updated_at" ->
          user.hasUpdatedAt() ? Optional.of(user.updateAtAsLong()) : Optional.empty();
      default -> Optional.empty();
    };
  }
}
