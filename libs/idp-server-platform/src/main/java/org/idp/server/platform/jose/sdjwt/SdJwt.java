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
package org.idp.server.platform.jose.sdjwt;

/**
 * An issued SD-JWT (RFC 9901) in its compact serialization: {@code <Issuer-signed JWT>~<Disclosure
 * 1>~...~<Disclosure N>~}, without a Key Binding JWT.
 */
public class SdJwt {

  String value;
  String issuerSignedJwt;
  int disclosureCount;

  SdJwt(String value, String issuerSignedJwt, int disclosureCount) {
    this.value = value;
    this.issuerSignedJwt = issuerSignedJwt;
    this.disclosureCount = disclosureCount;
  }

  public String value() {
    return value;
  }

  public String issuerSignedJwt() {
    return issuerSignedJwt;
  }

  public int disclosureCount() {
    return disclosureCount;
  }
}
