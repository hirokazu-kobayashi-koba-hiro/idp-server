package org.idp.server.core.oauth.grant;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.oauth.identity.RequestedIdTokenClaims;
import org.idp.server.core.type.oauth.ResponseType;
import org.idp.server.core.type.oauth.Scopes;

public class GrantIdTokenClaims implements Iterable<String> {

  Set<String> values;
  private static final String delimiter = " ";

  public GrantIdTokenClaims() {
    this.values = new HashSet<>();
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

    if (shouldAddVerifiedClaims(idTokenClaims)) claims.add("verified_claims");

    return new GrantIdTokenClaims(claims);
  }

  public GrantIdTokenClaims(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(delimiter)).collect(Collectors.toSet());
  }

  public GrantIdTokenClaims(Set<String> values) {
    this.values = values;
  }

  public GrantIdTokenClaims merge(GrantIdTokenClaims other) {
    Set<String> newValues = new HashSet<>(this.values);
    newValues.addAll(other.values);
    return new GrantIdTokenClaims(newValues);
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

  public boolean hasVerifiedClaims() {
    return Objects.nonNull(values) && values.contains("verified_claims");
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
      return idTokenClaims.hasGivenName();
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
      return idTokenClaims.hasProfile();
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
      return idTokenClaims.hasName();
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

  public static boolean shouldAddVerifiedClaims(RequestedIdTokenClaims idTokenClaims) {
    return idTokenClaims.hasVerifiedClaims();
  }

  public boolean contains(String claims) {
    return values.contains(claims);
  }
}
