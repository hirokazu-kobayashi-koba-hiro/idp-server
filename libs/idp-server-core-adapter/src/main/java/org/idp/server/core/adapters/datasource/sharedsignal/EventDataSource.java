package org.idp.server.core.adapters.datasource.sharedsignal;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.sharedsignal.Event;
import org.idp.server.core.sharedsignal.EventRepository;
import org.idp.server.core.sharedsignal.EventSearchCriteria;
import org.idp.server.core.sharedsignal.Events;

public class EventDataSource implements EventRepository {

  JsonConverter converter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(Event event) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                INSERT INTO public.events (id, type, description, tenant_id, tenant_name, client_id, client_name, user_id, user_name, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb);
                """;
    List<Object> params = new ArrayList<>();
    params.add(event.identifier().value());
    params.add(event.type().value());
    params.add(event.description().value());
    params.add(event.tenant().id());
    params.add(event.tenant().name());
    params.add(event.client().id());
    params.add(event.client().name());

    if (event.hasUser()) {
      params.add(event.user().id());
      params.add(event.user().name());
    } else {
      params.add(null);
      params.add(null);
    }
    params.add(converter.write(event.detail().toMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Events findBy(String eventServerId, String userId) {
    return null;
  }

  @Override
  public Events search(String eventServerId, EventSearchCriteria criteria) {
    return null;
  }
}
