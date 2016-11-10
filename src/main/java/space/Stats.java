package space;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class Stats {
    private int gets = 0;
    private int uncachedGets = 0;
    private Set<String> ids = new HashSet<>();

    void incGet() {
        gets++;
    }

    void incUncachedGet() {
        uncachedGets++;
    }

    void addId(String id) {
        ids.add(id);
    }

    String getStats() {
        return "Total gets: " + gets + ", uncached gets: " + uncachedGets + ", total unique contents: " + ids.size();
    }
}
