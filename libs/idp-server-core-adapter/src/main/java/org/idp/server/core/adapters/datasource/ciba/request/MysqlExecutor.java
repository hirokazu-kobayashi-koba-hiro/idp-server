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

package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;

public class MysqlExecutor implements BackchannelAuthenticationRequestSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(BackchannelAuthenticationRequest request) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                INSERT INTO backchannel_authentication_request
                (id, tenant_id, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

    List<Object> params = new ArrayList<>();
    params.add(request.identifier().value());
    params.add(request.tenantIdentifier().value());
    params.add(request.profile().name());
    params.add(request.deliveryMode().name());
    params.add(request.scopes().toStringValues());
    params.add(request.requestedClientId().value());

    if (request.hasIdTokenHint()) {
      params.add(request.idTokenHint().value());
    } else {
      params.add(null);
    }
    if (request.hasLoginHint()) {
      params.add(request.loginHint().value());
    } else {
      params.add(null);
    }
    if (request.hasLoginHintToken()) {
      params.add(request.loginHintToken().value());
    } else {
      params.add(null);
    }
    if (request.hasAcrValues()) {
      params.add(request.acrValues().toStringValues());
    } else {
      params.add(null);
    }
    if (request.hasUserCode()) {
      params.add(request.userCode().value());
    } else {
      params.add(null);
    }
    if (request.hasBindingMessage()) {
      params.add(request.bindingMessage().value());
    } else {
      params.add(null);
    }
    if (request.hasClientNotificationToken()) {
      params.add(request.clientNotificationToken().value());
    } else {
      params.add(null);
    }
    if (request.hasRequestedExpiry()) {
      params.add(request.requestedExpiry().value());
    } else {
      params.add(null);
    }
    if (request.hasRequest()) {
      params.add(request.requestObject().value());
    } else {
      params.add(null);
    }
    if (request.hasAuthorizationDetails()) {
      params.add(toJson(request.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                        SELECT id, tenant_id, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details
                        FROM backchannel_authentication_request
                        WHERE id = ?;
                        """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void delete(BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM backchannel_authentication_request WHERE id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
