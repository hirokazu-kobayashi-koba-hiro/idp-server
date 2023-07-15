package org.idp.server.oauth.identity;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import org.idp.server.verifiablecredential.VerifiableCredential;

public class User implements Serializable {
  String sub;
  String name;
  String givenName;
  String familyName;
  String middleName;
  String nickname;
  String preferredUsername;
  String profile;
  String picture;
  String website;
  String email;
  Boolean emailVerified;
  String gender;
  String birthdate;
  String zoneinfo;
  String locale;
  String phoneNumber;
  Boolean phoneNumberVerified;
  Address address;
  Long updatedAt;
  List<VerifiableCredential> verifiableCredentials;

  public String sub() {
    return sub;
  }

  public User setSub(String sub) {
    this.sub = sub;
    return this;
  }

  public String name() {
    return name;
  }

  public User setName(String name) {
    this.name = name;
    return this;
  }

  public String givenName() {
    return givenName;
  }

  public User setGivenName(String givenName) {
    this.givenName = givenName;
    return this;
  }

  public String familyName() {
    return familyName;
  }

  public User setFamilyName(String familyName) {
    this.familyName = familyName;
    return this;
  }

  public String middleName() {
    return middleName;
  }

  public User setMiddleName(String middleName) {
    this.middleName = middleName;
    return this;
  }

  public String nickname() {
    return nickname;
  }

  public User setNickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  public String preferredUsername() {
    return preferredUsername;
  }

  public User setPreferredUsername(String preferredUsername) {
    this.preferredUsername = preferredUsername;
    return this;
  }

  public String profile() {
    return profile;
  }

  public User setProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public String picture() {
    return picture;
  }

  public User setPicture(String picture) {
    this.picture = picture;
    return this;
  }

  public String website() {
    return website;
  }

  public User setWebsite(String website) {
    this.website = website;
    return this;
  }

  public String email() {
    return email;
  }

  public User setEmail(String email) {
    this.email = email;
    return this;
  }

  public boolean emailVerified() {
    return emailVerified;
  }

  public User setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
    return this;
  }

  public String gender() {
    return gender;
  }

  public User setGender(String gender) {
    this.gender = gender;
    return this;
  }

  public String birthdate() {
    return birthdate;
  }

  public User setBirthdate(String birthdate) {
    this.birthdate = birthdate;
    return this;
  }

  public String zoneinfo() {
    return zoneinfo;
  }

  public User setZoneinfo(String zoneinfo) {
    this.zoneinfo = zoneinfo;
    return this;
  }

  public String locale() {
    return locale;
  }

  public User setLocale(String locale) {
    this.locale = locale;
    return this;
  }

  public String phoneNumber() {
    return phoneNumber;
  }

  public User setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    return this;
  }

  public boolean phoneNumberVerified() {
    return phoneNumberVerified;
  }

  public User setPhoneNumberVerified(boolean phoneNumberVerified) {
    this.phoneNumberVerified = phoneNumberVerified;
    return this;
  }

  public Address address() {
    return address;
  }

  public User setAddress(Address address) {
    this.address = address;
    return this;
  }

  public long updateAt() {
    return updatedAt;
  }

  public User setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public List<VerifiableCredential> verifiableCredentials() {
    return verifiableCredentials;
  }

  public User setVerifiableCredentials(List<VerifiableCredential> verifiableCredentials) {
    this.verifiableCredentials = verifiableCredentials;
    return this;
  }

  public boolean exists() {
    return Objects.nonNull(sub) && !sub.isEmpty();
  }

  public boolean hasName() {
    return Objects.nonNull(name) && !name.isEmpty();
  }

  public boolean hasGivenName() {
    return Objects.nonNull(givenName) && !givenName.isEmpty();
  }

  public boolean hasFamilyName() {
    return Objects.nonNull(familyName) && !familyName.isEmpty();
  }

  public boolean hasMiddleName() {
    return Objects.nonNull(middleName) && !middleName.isEmpty();
  }

  public boolean hasNickname() {
    return Objects.nonNull(nickname) && !nickname.isEmpty();
  }

  public boolean hasPreferredUsername() {
    return Objects.nonNull(preferredUsername) && !preferredUsername.isEmpty();
  }

  public boolean hasProfile() {
    return Objects.nonNull(profile) && !profile.isEmpty();
  }

  public boolean hasPicture() {
    return Objects.nonNull(picture) && !picture.isEmpty();
  }

  public boolean hasWebsite() {
    return Objects.nonNull(website) && !website.isEmpty();
  }

  public boolean hasEmail() {
    return Objects.nonNull(email) && !email.isEmpty();
  }

  public boolean hasEmailVerified() {
    return Objects.nonNull(emailVerified);
  }

  public boolean hasGender() {
    return Objects.nonNull(gender) && !gender.isEmpty();
  }

  public boolean hasBirthdate() {
    return Objects.nonNull(birthdate) && !birthdate.isEmpty();
  }

  public boolean hasZoneinfo() {
    return Objects.nonNull(zoneinfo) && !zoneinfo.isEmpty();
  }

  public boolean hasLocale() {
    return Objects.nonNull(locale) && !locale.isEmpty();
  }

  public boolean hasPhoneNumber() {
    return Objects.nonNull(phoneNumber) && !phoneNumber.isEmpty();
  }

  public boolean hasPhoneNumberVerified() {
    return Objects.nonNull(phoneNumberVerified);
  }

  public boolean hasAddress() {
    return Objects.nonNull(address) && address.exists();
  }

  public boolean hasUpdatedAt() {
    return Objects.nonNull(updatedAt);
  }

  public boolean hasVerifiableCredentials() {
    return Objects.nonNull(verifiableCredentials) && !verifiableCredentials.isEmpty();
  }
}
