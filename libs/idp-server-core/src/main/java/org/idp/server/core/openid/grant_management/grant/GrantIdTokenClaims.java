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

package org.idp.server.core.openid.grant_management.grant;

import java.util.*;
import org.idp.server.core.openid.identity.id_token.RequestedIdTokenClaims;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.oauth.type.oauth.ResponseType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;

public class GrantIdTokenClaims implements Iterable<String> {

  Set<String> values;
  // The OIDC4IDA verified_claims request persisted with the grant, so the ID Token can be built
  // from the consent record at issuance time without the original claims parameter. Mirrors
  // GrantUserinfoClaims. (#1628 follow-up)
  RequestedVerifiedClaims requestedVerifiedClaims;
  private static final String delimiter = " ";

  public GrantIdTokenClaims() {
    this.values = new HashSet<>();
    this.requestedVerifiedClaims = RequestedVerifiedClaims.empty();
  }

  public static GrantIdTokenClaims create(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    Set<String> claims = new HashSet<>();
    if (shouldAddName(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("name");
    if (shouldAddGivenName(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("given_name");
    if (shouldAddFamilyName(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("family_name");
    if (shouldAddMiddleName(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("middle_name");
    if (shouldAddNickname(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("nickname");
    if (shouldAddPreferredUsername(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("preferred_username");
    if (shouldAddProfile(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("profile");
    if (shouldAddPicture(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("picture");
    if (shouldAddWebsite(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("website");
    if (shouldAddEmail(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("email");
    if (shouldAddEmailVerified(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("email_verified");
    if (shouldAddGender(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("gender");
    if (shouldAddBirthdate(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("birthdate");
    if (shouldAddZoneinfo(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("zoneinfo");
    if (shouldAddLocale(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("locale");
    if (shouldAddPhoneNumber(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("phone_number");
    if (shouldAddPhoneNumberVerified(
        scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("phone_number_verified");
    if (shouldAddAddress(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("address");
    if (shouldAddUpdatedAt(scopes, responseType, supportedClaims, idTokenClaims, idTokenStrictMode))
      claims.add("updated_at");

    // verified_claims is carried as a sentinel (vc:<base64url(JSON)>) holding the full requested
    // structure, not as a bare "verified_claims" name token — so the consent record is
    // self-contained and the ID Token can be rebuilt from the grant alone. (#1628 follow-up)
    return new GrantIdTokenClaims(
        claims, RequestedVerifiedClaims.of(idTokenClaims.verifiedClaims()));
  }

  public GrantIdTokenClaims(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      this.requestedVerifiedClaims = RequestedVerifiedClaims.empty();
      return;
    }
    Set<String> names = new HashSet<>();
    RequestedVerifiedClaims parsed = RequestedVerifiedClaims.empty();
    for (String token : value.split(delimiter)) {
      if (RequestedVerifiedClaims.isSentinel(token)) {
        parsed = RequestedVerifiedClaims.fromSentinel(token);
      } else {
        names.add(token);
      }
    }
    this.values = names;
    this.requestedVerifiedClaims = parsed;
  }

  public GrantIdTokenClaims(Set<String> values) {
    this.values = values;
    this.requestedVerifiedClaims = RequestedVerifiedClaims.empty();
  }

  public GrantIdTokenClaims(Set<String> values, RequestedVerifiedClaims requestedVerifiedClaims) {
    this.values = values;
    this.requestedVerifiedClaims = requestedVerifiedClaims;
  }

  public GrantIdTokenClaims merge(GrantIdTokenClaims other) {
    Set<String> newValues = new HashSet<>(this.values);
    newValues.addAll(other.values);
    // The latest request's verified_claims supersedes; fall back to the existing one.
    RequestedVerifiedClaims mergedVerifiedClaims =
        other.requestedVerifiedClaims.exists()
            ? other.requestedVerifiedClaims
            : this.requestedVerifiedClaims;
    return new GrantIdTokenClaims(newValues, mergedVerifiedClaims);
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return (values != null && !values.isEmpty()) || requestedVerifiedClaims.exists();
  }

  public Set<String> toStringSet() {
    return values;
  }

  public String toStringValues() {
    String names = String.join(delimiter, values);
    if (!requestedVerifiedClaims.exists()) {
      return names;
    }
    String sentinel = requestedVerifiedClaims.toSentinelToken();
    return names.isEmpty() ? sentinel : names + delimiter + sentinel;
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

  public boolean hasVerifiedClaims() {
    // New rows carry the request as a sentinel; legacy rows carry the bare "verified_claims" name
    // token (pre-sentinel). Recognize both so existing grants keep working.
    return requestedVerifiedClaims.exists()
        || (Objects.nonNull(values) && values.contains("verified_claims"));
  }

  /**
   * The requested verified_claims structure persisted with the grant, or {@code null} for legacy
   * grants that recorded only the bare name token — in which case the caller falls back to the live
   * request. (#1628 follow-up)
   */
  public VerifiedClaimsObject verifiedClaims() {
    return requestedVerifiedClaims.exists() ? requestedVerifiedClaims.toObject() : null;
  }

  private static boolean shouldAddName(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("name")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile") || idTokenClaims.hasName();
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasName();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddGivenName(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("given_name")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasGivenName();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddFamilyName(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("family_name")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasFamilyName();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddMiddleName(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("middle_name")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasMiddleName();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddNickname(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("nickname")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasNickname();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddPreferredUsername(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("preferred_username")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasPreferredUsername();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddProfile(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("profile")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasProfile();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddPicture(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("picture")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasPicture();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddWebsite(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("website")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasWebsite();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddEmail(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("email")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("email");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasEmail();
    }
    return scopes.contains("email");
  }

  private static boolean shouldAddEmailVerified(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("email_verified")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("email");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasEmailVerified();
    }
    return scopes.contains("email");
  }

  private static boolean shouldAddGender(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("gender")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasGender();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddBirthdate(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("birthdate")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasBirthdate();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddZoneinfo(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("zoneinfo")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasZoneinfo();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddLocale(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("locale")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasLocale();
    }
    return scopes.contains("profile");
  }

  private static boolean shouldAddPhoneNumber(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("phone_number")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("phone");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasPhoneNumber();
    }
    return scopes.contains("phone");
  }

  private static boolean shouldAddPhoneNumberVerified(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("phone_number_verified")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("phone");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasPhoneNumberVerified();
    }
    return scopes.contains("phone");
  }

  private static boolean shouldAddAddress(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("address")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("address");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasAddress();
    }
    return scopes.contains("address");
  }

  private static boolean shouldAddUpdatedAt(
      Scopes scopes,
      ResponseType responseType,
      List<String> supportedClaims,
      RequestedIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode) {
    if (!supportedClaims.contains("updated_at")) {
      return false;
    }
    if (responseType.isOidcIdTokenOnlyImplicitFlow()) {
      return scopes.contains("profile");
    }
    if (idTokenStrictMode) {
      return idTokenClaims.hasUpdatedAt();
    }
    return scopes.contains("profile");
  }

  public boolean contains(String claims) {
    return values.contains(claims);
  }
}
