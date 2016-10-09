package space;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FeignClient(name = "content-api", url = "${content-api-base-url}")
interface ContentApi {

    @RequestMapping(method = RequestMethod.GET, value = "/content/view/the-localhost/{aliasandid}?variant=web")
    Map<String, Object> content(@RequestParam("aliasandid") String id, @RequestHeader("X-Auth-Token") String token);

    @Cacheable("content")
    default Map<String, Object> content(@RequestParam("aliasandid") String id) {
        return content(unversioned(id), DispatcherApplication.AUTH_TOKEN);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/content/{aliasandid}?variant=web")
    Map<String, Object> versionedContent(@RequestParam("aliasandid") String id, @RequestHeader("X-Auth-Token") String token);

    default Map<String, Object> versionedContent(@RequestParam("aliasandid") String id) {
        return versionedContent(id, DispatcherApplication.AUTH_TOKEN);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/search/onecms/select?q={q}&view=the-localhost&wt=json&rows=50&sort=publishDate_dt+desc")
    Map<String, Object> search(@RequestParam("q") String q, @RequestHeader("X-Auth-Token") String token);

    //@Cacheable("search")
    default Map<String, Object> search(@RequestParam("q") String q) {
        return search(q, DispatcherApplication.AUTH_TOKEN);
    }

    default List<Map<String, Object>> batch(List<String> ids) {
        List<Map<String, Object>> contents = new ArrayList<>();
        ids.forEach(id -> {
            contents.add(content(id));
        });
        return contents;
    }

    default String translateSymbolicId(String symbolicId) {
        return ContentMapUtil.getString(content(symbolicId), "id");
    }

    default boolean isSymbolicId(String id) {
        return id.startsWith("uuid");
    }

    default String unversioned(String id) {
        // strip off version - temporary workaround
        Pattern pattern = Pattern.compile("(.*:.*):.*");
        Matcher matcher = pattern.matcher(id);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return id;
    }
}
