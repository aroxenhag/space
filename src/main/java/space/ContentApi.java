package space;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@FeignClient(name = "content-api", url = "${content-api-base-url}")
interface ContentApi {

    default Map<String, Object> content(String alias, String id) {
        return content(alias + "/" + id);
    }

    @Cacheable("content")
    @RequestMapping(method = RequestMethod.GET, value = "/content/{aliasandid}")
    Map<String, Object> content(@RequestParam("aliasandid") String id);

    @Cacheable("search")
    @RequestMapping(method = RequestMethod.GET, value = "/search/onecms/select?q={q}&wt=json&rows=20&sort=modificationTime+desc")
    Map<String, Object> search(@RequestParam("q") String q);

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
}
