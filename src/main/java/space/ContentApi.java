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
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContentApi {

    @Value("${content-api-base-url}")
    private String contentApiBaseUrl = "http://localhost:8080";

    public static int totalContentGets = 0;
    public static int uncachedContentGets = 0;
    public static Set<String> ids = new HashSet<>();

    @Autowired
    ContentApiInternal contentApiInternal;


    @Component
    class ContentApiInternal {
        @CacheEvict("unversioned")
        public void evictUnversionedId(String id) {
            // Intentionally left empty
        }

        @Cacheable("content")
        public Map<String, Object> getContentVersion(String id) {

            uncachedContentGets++;

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

        // returns unversioned id, populates unversioned to versioned mapping
        @Cacheable("alias")
        public String resolveSymbolicId(String id) {
            String versionedId = resolve(id);
            String unversionedId = unversioned(versionedId);
            cachePutUnversionedMapping(unversionedId, versionedId);
            return unversionedId;
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

        @CachePut(cacheNames = "unversioned", key = "#unversionedId")
        String cachePutUnversionedMapping(String unversionedId, String versionedId) {
            // Just here to populate cache
            return versionedId;
        }

        @Cacheable("unversioned")
        public String resolveUnversionedId(String id) {
            String resolved = resolve(id);
            return resolved;
        }
    }

    public void evict(String id) {
        contentApiInternal.evictUnversionedId(id);
    }

    // returns versioned id
    public String resolveSymbolicId(String id) {
        String unversionedId = contentApiInternal.resolveSymbolicId(id);
        return contentApiInternal.resolveUnversionedId(unversionedId);
    }

    public String resolveUnversionedId(String id) {
        return contentApiInternal.resolveUnversionedId(id);
    }

    public Map<String, Object> getContent(String id) {
        totalContentGets++;
        ids.add(id);

        if (isSymbolicId(id)) {
            String uvId = contentApiInternal.resolveSymbolicId(id);
            id = contentApiInternal.resolveUnversionedId(uvId);
        } else if (!isVersionedId(id)) {
            String uvId = id;
            id = contentApiInternal.resolveUnversionedId(id);
        }

        return contentApiInternal.getContentVersion(id);
    }


    public List<Map<String, Object>> batch(List<String> ids) {
        List<Map<String, Object>> contents = new ArrayList<>();
        ids.forEach(id -> {
            contents.add(getContent(id));
        });

        return contents;
    }

    private void setAuthHeader(HttpGet httpGet) {
        httpGet.setHeader("X-Auth-Token", DispatcherApplication.AUTH_TOKEN);
    }

    public boolean isSymbolicId(String id) {
        return id.startsWith("uuid/") || id.startsWith("friendly/");
    }

    private boolean isVersionedId(String id) {
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        return matcher.matches();
    }

    public static String unversioned(String id) {
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return id;
    }

    public Map<String, Object> search(String q) {

        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            String encodedQuery = URLEncoder.encode(q, "UTF-8");
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/search/onecms/select?q=" + encodedQuery + "&view=the-localhost&wt=json&rows=50&sort=publishDate_dt+desc");
            setAuthHeader(httpGet);
            HttpResponse response = httpClient.execute(httpGet);
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

            Map<String, Object> map = new Gson().fromJson(responseString, Map.class);
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
