package com.haoleme.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local pin order: pinned ids stay at the front in pin sequence, others keep relative order. */
final class RunListOrder {
    private RunListOrder() {
    }

    static List<String> orderIds(List<String> ids, List<String> pinnedOrder) {
        List<String> source = ids == null ? new ArrayList<String>() : ids;
        List<String> pinned = pinnedOrder == null ? new ArrayList<String>() : pinnedOrder;
        if (pinned.isEmpty() || source.isEmpty()) {
            return new ArrayList<>(source);
        }
        Set<String> pinSet = new HashSet<>(pinned);
        Map<String, Boolean> present = new HashMap<>();
        List<String> rest = new ArrayList<>();
        for (String id : source) {
            if (id == null || id.isEmpty() || "__empty__".equals(id)) {
                continue;
            }
            if (pinSet.contains(id)) {
                present.put(id, Boolean.TRUE);
            } else {
                rest.add(id);
            }
        }
        List<String> out = new ArrayList<>();
        for (String id : pinned) {
            if (Boolean.TRUE.equals(present.get(id))) {
                out.add(id);
            }
        }
        out.addAll(rest);
        return out;
    }
}
