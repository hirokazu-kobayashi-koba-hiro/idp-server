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
package org.idp.server.account_linking;

/**
 * What {@link LinkedExternalAccountTokenService} came back with: the access token, and whether the
 * external IdP had to be asked for a new one to get it.
 */
public class ExternalAccessTokenAcquisition {

  ExternalAccessToken accessToken;
  boolean refreshed;

  public ExternalAccessTokenAcquisition(ExternalAccessToken accessToken, boolean refreshed) {
    this.accessToken = accessToken;
    this.refreshed = refreshed;
  }

  public ExternalAccessToken accessToken() {
    return accessToken;
  }

  /** Whether the stored token was refreshed at the external IdP to answer this request. */
  public boolean isRefreshed() {
    return refreshed;
  }
}
