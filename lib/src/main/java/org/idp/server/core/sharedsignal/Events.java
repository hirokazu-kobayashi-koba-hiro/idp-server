package org.idp.server.core.sharedsignal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Events implements Iterable<Event> {

    List<Event> values;

    public Events() {
        values = new ArrayList<>();
    }

    public Events(List<Event> values) {
        this.values = values;
    }

    @Override
    public Iterator<Event> iterator() {
        return values.iterator();
    }
}
