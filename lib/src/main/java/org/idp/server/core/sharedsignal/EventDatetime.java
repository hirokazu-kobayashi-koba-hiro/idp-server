package org.idp.server.core.sharedsignal;

import java.time.LocalDateTime;

public class EventDatetime {

    LocalDateTime value;

    public EventDatetime() {}

    public EventDatetime(LocalDateTime value) {
        this.value = value;
    }

    public LocalDateTime value() {
        return value;
    }

    public String valueAsString() {
        return value.toString();
    }
}
