package org.idp.server.core.sharedsignal;

public interface EventRepository {
  void register(Event event);

  Events findBy(String eventServerId, String userId);

  Events search(String eventServerId, EventSearchCriteria criteria);
}
