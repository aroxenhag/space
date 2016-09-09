package space;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@FeignClient(name = "content-api", url = "http://localhost:8080/ace")
interface ContentApi {
    @Cacheable("content")
    @RequestMapping(method = RequestMethod.GET, value = "/content/{alias}/{id}")
    Map<String, Object> content(@RequestParam("alias") String alias, @RequestParam("id") String id);

    @Cacheable("content2")
    @RequestMapping(method = RequestMethod.GET, value = "/content/{aliasandid}")
    Map<String, Object> content(@RequestParam("aliasandid") String id);

    @RequestMapping(method = RequestMethod.GET, value = "/search/onecms/select?q={q}&wt=json&rows=20")
    Map<String, Object> search(@RequestParam("q") String q);

    default List<Map<String, Object>> batch(List<String> ids) {
        List<Map<String, Object>> contents = new ArrayList<>();
        ids.forEach(id -> {
            contents.add(content(id));
        });
        return contents;
    }
}
