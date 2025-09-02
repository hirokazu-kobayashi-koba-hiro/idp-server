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

package org.idp.server.platform.datasource;

public enum SqlError {
  UNIQUE_VIOLATION, // 409
  FK_VIOLATION, // 409 or 400
  NOT_NULL_VIOLATION, // 400
  CHECK_VIOLATION, // 400
  SERIALIZATION_FAILURE, // 503 or 409
  DEADLOCK_DETECTED, // 503
  OTHER
}
