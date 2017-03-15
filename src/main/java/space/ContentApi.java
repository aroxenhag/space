package space;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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

    // not for content versions.
    public Map<String, Object> getContent(String alias) {
        // resolve to versioned id. return content
        // alias -> content id -> content version

        if (isVersionedId(alias)) {
            return contentApiInternal.getContentVersion(alias);
        } else {
            // if other alias than contentid, resolve to content id
            String version;
            if (!alias.startsWith("contentid/")) {
                version = resolveSymbolicId(alias);
            } else {
                version = resolveUnversionedId(alias);
            }


    //        if (isSymbolicId(alias)) {
    //            alias = resolveSymbolicId(alias);
    //        } else if (!isVersionedId(alias)) {
    //            alias = resolveUnversionedId(alias);
    //        }


    //        LogStatsFilter.getStats().incGet();
    //        LogStatsFilter.getStats().addId(unversioned(alias));


            return contentApiInternal.getContentVersion(version);
        }
    }

    public List<Map<String, Object>> batch(List<String> ids) {
        List<Map<String, Object>> contents = new ArrayList<>();
        ids.forEach(id -> {
            contents.add(getContent(id));
        });

        return contents;
    }

//    public boolean isSymbolicId(String id) {
//        return id.startsWith("uuid/") || id.startsWith("friendly/") || id.startsWith("ace-starterkit/");
//    }

    public boolean isVersionedId(String id) {
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        return matcher.matches();
    }
//
//    public static String unversioned(String id) {
//        Pattern pattern = Pattern.compile("(.*:.*):.*");
//        Matcher matcher = pattern.matcher(id);
//        if (matcher.matches()) {
//            return matcher.group(1);
//        }
//        return id;
//    }

    public Map<String, Object> search(String q, int limit) {

        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            String encodedQuery = URLEncoder.encode(q, "UTF-8");
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/search/onecms/select?fl=id&q=" + encodedQuery + "&view=p.public&wt=json&rows=" + limit + "&sort=publishDate_dt+desc");
            setAuthHeader(httpGet);
            HttpResponse response = httpClient.execute(httpGet);
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

            return new Gson().fromJson(responseString, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Component
    class ContentApiInternal {
        // Returns unversioned id. Populates unversioned to versioned mapping.
        @Cacheable("alias")
        public String resolveSymbolicId(String alias) {
            Map<String, Object> content = getContent(alias);

            String id = ContentMapUtil.getId(content);
            String version = ContentMapUtil.getVersion(content);

            cachePutUnversionedMapping(id, version);
            cachePutContentMapping(version, content);

            return id;
        }

        @Cacheable("unversioned")
        public String resolveUnversionedId(String id) {
            Map<String, Object> content = getContent(id);

            String version = ContentMapUtil.getVersion(content);

            cachePutContentMapping(version, content);

            return version;
        }

        public
        @CachePut(cacheNames = "content", key = "#version")
        Map<String, Object> cachePutContentMapping(String version, Map<String, Object> content) {
            // Just here to populate cache
            return content;
        }

        public
        @CachePut(cacheNames = "unversioned", key = "#unversionedId")
        String cachePutUnversionedMapping(String unversionedId, String versionedId) {
            // Just here to populate cache
            return versionedId;
        }

        @CacheEvict("unversioned")
        public void evictUnversionedId(String id) {
            // Intentionally left empty
        }


        public Map<String, Object> getContent(String alias) {

            LogStatsFilter.getStats().incUncachedGet();

            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/content/view/p.public/alias/" + alias + "?variant=web");
            setAuthHeader(httpGet);
            try {
                HttpResponse response = httpClient.execute(httpGet);

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

                    return new Gson().fromJson(responseString, Map.class);
                } else {
                    throw new RuntimeException("Error getting content. Response: " + response + ", request: " + httpGet);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Cacheable("content")
        public Map<String, Object> getContentVersion(String version) {

            LogStatsFilter.getStats().incUncachedGet();

            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpGet httpGet = new HttpGet(contentApiBaseUrl + "/content/version/" + version + "?variant=web");
            setAuthHeader(httpGet);
            try {
                HttpResponse response = httpClient.execute(httpGet);
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

                    return new Gson().fromJson(responseString, Map.class);
                } else {
                    throw new RuntimeException("Error getting content. Response: " + response + ", request: " + httpGet);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

//        private String resolve(String id) {
//            HttpClient httpClient = HttpClientBuilder.create().disableRedirectHandling().build();
//            String url = contentApiBaseUrl + "/content/view/p.public/alias/" + id;
//            HttpGet httpGet = new HttpGet(url);
//            setAuthHeader(httpGet);
//            try {
//                HttpResponse response = httpClient.execute(httpGet);
//
//                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_SEE_OTHER) {
//                    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
//
//                    Map<String, String> map = new Gson().fromJson(responseString, Map.class);
//                    String location = map.get("location");
//
//                    String versionedId = location.substring("/ace/content/".length());
//
//                    return versionedId;
//                } else {
//                    throw new RuntimeException("Error resolving id on relevant view: " + id + "(url: " + url + ")");
//                }
//            } catch (Exception e) {
//                throw new RuntimeException("Error resolving id: " + id, e);
//            }
//        }
    }

    private void setAuthHeader(HttpGet httpGet) {
        httpGet.setHeader("X-Auth-Token", DispatcherApplication.AUTH_TOKEN);
    }
}
