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

package org.idp.server.core.openid.authentication;

public enum StandardAuthenticationMethodReference {
  FACE("face", "Biometric authentication [RFC4949] using facial recognition."),
  FPT("fpt", "Biometric authentication [RFC4949] using a fingerprint."),
  GEO(
      "geo",
      "Use of geolocation information for authentication, such as that provided by [W3C.REC-geolocation-API-20161108]."),
  HWK(
      "hwk",
      "Proof-of-Possession (PoP) of a hardware-secured key. See Appendix C of [RFC4211] for a discussion on PoP."),
  IRIS("iris", "Biometric authentication [RFC4949] using an iris scan."),
  KBA("kba", "Knowledge-based authentication [NIST.800-63-2] [ISO29115]."),
  MCA(
      "mca",
      "Multiple-channel authentication [MCA]. The authentication involves communication over more than one distinct communication channel. For instance, a multiple-channel authentication might involve both entering information into a workstation's browser and providing information on a telephone call to a pre-registered number."),
  MFA(
      "mfa",
      "Multiple-factor authentication [NIST.800-63-2] [ISO29115]. When this is present, specific authentication methods used may also be included."),
  OTP(
      "otp",
      "One-time password [RFC4949]. One-time password specifications that this authentication method applies to include [RFC4226] and [RFC6238]."),
  PIN(
      "pin",
      "Personal Identification Number (PIN) [RFC4949] or pattern (not restricted to containing only numbers) that a user enters to unlock a key on the device. This mechanism should have a way to deter an attacker from obtaining the PIN by trying repeated guesses."),
  PWD("pwd", "Password-based authentication [RFC4949]."),
  RBA("rba", "Risk-based authentication [JECM]."),
  RETINA("retina", "Biometric authentication [RFC4949] using a retina scan."),
  SC("sc", "Smart card [RFC4949]."),
  SMS("sms", "Confirmation using SMS [SMS] text message to the user at a registered number."),
  SWK(
      "swk",
      "Proof-of-Possession (PoP) of a software-secured key. See Appendix C of [RFC4211] for a discussion on PoP."),
  TEL(
      "tel",
      "Confirmation by telephone call to the user at a registered number. This authentication technique is sometimes also referred to as \"call back\" [RFC4949]."),
  USER(
      "user",
      "User presence test. Evidence that the end user is present and interacting with the device. This is sometimes also referred to as \"test of user presence\" [W3C.WD-webauthn-20170216]."),
  VBM("vbm", "Biometric authentication [RFC4949] using a voiceprint."),
  WIA("wia", "Windows integrated authentication [MSDN]."),
  UNKNOWN("unknown", "Unknown authentication method [RFC4949]."),
  ;

  private final String value;
  private final String description;

  StandardAuthenticationMethodReference(String value, String description) {
    this.value = value;
    this.description = description;
  }

  public String value() {
    return value;
  }

  public String description() {
    return description;
  }

  public static StandardAuthenticationMethodReference from(String value) {
    for (StandardAuthenticationMethodReference method : values()) {
      if (method.value.equalsIgnoreCase(value)) {
        return method;
      }
    }
    return UNKNOWN;
  }
}
