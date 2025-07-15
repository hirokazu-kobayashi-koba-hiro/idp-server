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

package org.idp.server.core.oidc.authentication.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;

public class AuthenticationTransactionFindingListResponse {

  int statusCode;
  List<AuthenticationTransaction> authenticationTransactions;

  public static AuthenticationTransactionFindingListResponse success(
      List<AuthenticationTransaction> authenticationTransactions) {
    return new AuthenticationTransactionFindingListResponse(200, authenticationTransactions);
  }

  public static AuthenticationTransactionFindingListResponse notFound() {
    return new AuthenticationTransactionFindingListResponse(404, List.of());
  }

  private AuthenticationTransactionFindingListResponse(
      int statusCode, List<AuthenticationTransaction> authenticationTransactions) {
    this.statusCode = statusCode;
    this.authenticationTransactions = authenticationTransactions;
  }

  public int statusCode() {
    return statusCode;
  }

  public List<AuthenticationTransaction> authenticationTransactions() {
    return authenticationTransactions;
  }

  public Map<String, Object> contents() {
    if (statusCode == 200) {
      Map<String, Object> map = new HashMap<>();
      List<Map<String, Object>> list =
          authenticationTransactions.stream().map(AuthenticationTransaction::toRequestMap).toList();
      map.put("list", list);
      return map;
    }
    return Map.of();
  }
}
