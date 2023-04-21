package org.idp.server.oauth.identity;

public class User {
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
  boolean emailVerified;
  String gender;
  String birthdate;
  String zoneinfo;
  String locale;
  String phoneNumber;
  boolean phoneNumberVerified;
  // address
  long updateAt;

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

  public long updateAt() {
    return updateAt;
  }

  public User setUpdateAt(long updateAt) {
    this.updateAt = updateAt;
    return this;
  }
}
