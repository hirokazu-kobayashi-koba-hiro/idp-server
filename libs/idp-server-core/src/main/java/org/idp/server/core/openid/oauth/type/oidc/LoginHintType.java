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

package org.idp.server.core.openid.oauth.type.oidc;

/**
 * Represents the type of login hint based on its prefix.
 *
 * <p>Login hints can specify different types of user identifiers:
 *
 * <ul>
 *   <li>DEVICE - Device ID hint (format: "device:{deviceId}")
 *   <li>SUBJECT - Subject identifier hint (format: "sub:{sub}")
 *   <li>EXTERNAL_SUBJECT - External IdP subject hint (format: "ex-sub:{sub}")
 *   <li>EMAIL - Email address hint (format: "email:{email}")
 *   <li>PHONE - Phone number hint (format: "phone:{phoneNumber}")
 *   <li>UNKNOWN - Unrecognized format or no prefix
 * </ul>
 */
public enum LoginHintType {
  /** Device ID hint (format: "device:{deviceId}") */
  DEVICE,

  /** Subject identifier hint (format: "sub:{sub}") */
  SUBJECT,

  /** External IdP subject hint (format: "ex-sub:{sub}") */
  EXTERNAL_SUBJECT,

  /** Email address hint (format: "email:{email}") */
  EMAIL,

  /** Phone number hint (format: "phone:{phoneNumber}") */
  PHONE,

  /** Unrecognized format or no prefix */
  UNKNOWN
}
