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

package org.idp.server.authentication.interactors.device;

import org.idp.server.platform.json.JsonReadable;

/**
 * Number-matching code stored by {@link AuthenticationDeviceNumberMatchingChallengeInteractor} so
 * the later {@link AuthenticationDeviceNumberMatchingInteractor} can verify the value the user
 * transcribed from the sign-in screen (push fatigue mitigation, Issue #1505). The code is held
 * server-side only and is never sent to the device.
 */
public class NumberMatchingChallenge implements JsonReadable {

  String code;

  public NumberMatchingChallenge() {}

  public NumberMatchingChallenge(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
