package space;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class LogStatsFilter implements Filter {

    @Autowired
    private CounterService counterService;

    private final static ThreadLocal<Stats> stats = new ThreadLocal<Stats>() {
        @Override
        protected Stats initialValue() {
            return new Stats();
        }
    };

    public static Stats getStats() {
        return stats.get();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        getStats().reset();
        filterChain.doFilter(servletRequest, servletResponse);

        if (getStats().isInitialized()) {
            counterService.increment("space.gets");
            System.out.println(getStats().getStats());
        }
    }

    @Override
    public void destroy() {

    }

    public static class Stats {
        private int gets = 0;
        private int uncachedGets = 0;
        private Set<String> ids = new HashSet<>();
        private boolean initialized;

        void initialize() {
            this.initialized = true;
        }

        void incGet() {
            gets++;
        }

        void incUncachedGet() {
            uncachedGets++;
        }

        void addId(String id) {
            ids.add(id);
        }

        public void reset() {
            gets = 0;
            uncachedGets = 0;
            ids.clear();
            this.initialized = false;
        }

        String getStats() {
            return "Total gets: " + gets + ", uncached gets: " + uncachedGets + ", total unique contents: " + ids.size();
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

}
