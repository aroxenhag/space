package space;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContentApi {

    @Value("${content-api-base-url}")
    private String contentApiBaseUrl;

    @Autowired
    ContentApiInternal contentApiInternal;

    @EventListener(condition = "#event.draft == false and #event.type == 'mutation'")
    public void evict(ContentEventListener.ContentEvent event) {
        contentApiInternal.evictUnversionedId(event.getId());
    }

    // returns versioned id
    public String resolveSymbolicId(String id) {
        String unversionedId = contentApiInternal.resolveSymbolicId(id);
        return resolveUnversionedId(unversionedId);
    }

    public String resolveUnversionedId(String id) {
        return contentApiInternal.resolveUnversionedId(id);
    }

    public Map<String, Object> getContent(String id) {
        if (isSymbolicId(id)) {
            id = resolveSymbolicId(id);
        } else if (!isVersionedId(id)) {
            id = resolveUnversionedId(id);
        }

        LogStatsFilter.getStats().incGet();
        LogStatsFilter.getStats().addId(unversioned(id));

        return contentApiInternal.getContentVersion(id);
    }


    public List<Map<String, Object>> batch(List<String> ids) {
        List<Map<String, Object>> contents = new ArrayList<>();
        ids.forEach(id -> {
            contents.add(getContent(id));
        });

        return contents;
    }

    public boolean isSymbolicId(String id) {
        return id.startsWith("uuid/") || id.startsWith("friendly/");
    }

    public static String unversioned(String id) {
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return id;
    }

    public Map<String, Object> search(String q, int limit) {

        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            String encodedQuery = URLEncoder.encode(q, "UTF-8");
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/search/onecms/select?q=" + encodedQuery + "&view=the-localhost&wt=json&rows=" + limit + "&sort=publishDate_dt+desc");
            setAuthHeader(httpGet);
            HttpResponse response = httpClient.execute(httpGet);
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

            Map<String, Object> map = new Gson().fromJson(responseString, Map.class);
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Component
    class ContentApiInternal {
        // Returns unversioned id. Populates unversioned to versioned mapping.
        @Cacheable("alias")
        public String resolveSymbolicId(String id) {
            String versionedId = resolve(id);
            String unversionedId = unversioned(versionedId);
            cachePutUnversionedMapping(unversionedId, versionedId);
            return unversionedId;
        }

        @Cacheable("unversioned")
        public String resolveUnversionedId(String id) {
            return resolve(id);
        }

        @CachePut(cacheNames = "unversioned", key = "#unversionedId")
        String cachePutUnversionedMapping(String unversionedId, String versionedId) {
            // Just here to populate cache
            return versionedId;
        }

        @CacheEvict("unversioned")
        public void evictUnversionedId(String id) {
            // Intentionally left empty
        }

        @Cacheable("content")
        public Map<String, Object> getContentVersion(String id) {

            LogStatsFilter.getStats().incUncachedGet();

            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/content/" + id + "?variant=web");
            setAuthHeader(httpGet);
            try {
                HttpResponse response = httpClient.execute(httpGet);
                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

                return new Gson().fromJson(responseString, Map.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String resolve(String id) {
            HttpClient httpClient = HttpClientBuilder.create().disableRedirectHandling().build();
            String url = contentApiBaseUrl + "/content/view/the-localhost/" + id + "?variant=web"; // TODO: Don't really care about variant here
            HttpGet httpGet = new HttpGet(url);
            setAuthHeader(httpGet);
            try {
                HttpResponse response = httpClient.execute(httpGet);

                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

                Map<String, String> map = new Gson().fromJson(responseString, Map.class);
                String location = map.get("location");

                String versionedId = "contentid/" + location.substring("/ace/content/contentid/".length(), location.indexOf("?"));

                return versionedId;
            } catch (Exception e) {
                throw new RuntimeException("Error resolving id: " + id, e);
            }
        }
    }

    private void setAuthHeader(HttpGet httpGet) {
        httpGet.setHeader("X-Auth-Token", DispatcherApplication.AUTH_TOKEN);
    }

    private boolean isVersionedId(String id) {
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        return matcher.matches();
    }
}
