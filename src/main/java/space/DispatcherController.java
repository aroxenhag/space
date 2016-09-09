package space;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@EnableFeignClients
public class DispatcherController {

    @Autowired
    private ContentApi contentApi;

    @RequestMapping(value = "{path:(?!static|error).*$}/**")
    public String dispatch(HttpServletRequest request, Map<String, Object> model) {

        List<String> friendlyAliasPath = getFriendlyAliasPath(request);
        List<String> contentPath = new ArrayList<>();
        friendlyAliasPath.forEach(s -> {
            contentPath.add("friendly/" + s);
        });

        List<Map<String, Object>> contents = contentApi.batch(contentPath);

        Map site = contents.get(0);
        model.put("site", site);

        Map section = site;
        Map article = null;
        for (Map content : contents) {
            String type = ContentMapUtil.getType(content);
            if ("section".equals(type)) {
                section = content;
            } else if ("article".equals(type)) {
                article = content;
            }
        }
        model.put("section", section);
        model.put("article", article);

        // Get all sections
        List sectionIds = (List) ContentMapUtil.getObject(site, "aspects.contentData.data.sections");
        List<Map<String, Object>> sections = new ArrayList<>();
        if (sectionIds != null) {
            sectionIds.forEach(sectionId -> {
                sections.add(contentApi.content((String) sectionId));
            });
        }
        model.put("sections", sections);

        // Get articles
        Map searchResult = contentApi.search("type:article");
        List docs = (List) ContentMapUtil.getObject(searchResult, "response.docs");
        List<Map<String, Object>> articles = new ArrayList<>();
        docs.forEach(doc -> {
            String articleId = ContentMapUtil.getString((Map<String, Object>) doc, "id");
            articles.add(contentApi.content("contentid", articleId));
        });
        model.put("articles", articles);

        // Build layout
        List<Integer> layoutConfig = Arrays.asList(new Integer[]{1, 2, 1, 4, 3});
        int articleIndex = 0;
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        outer: for (int cols : layoutConfig) {
            List<Map<String, Object>> rowContent = new ArrayList<>();
            for (int i = 0; i < cols; i++) {
                if (articleIndex >= articles.size()) {
                    break outer;
                }
                rowContent.add(articles.get(articleIndex++));
            }
            rows.add(rowContent);
        }
        model.put("rows", rows);

        // Add generic tools
        model.put("curl", new ContentUrlCreator(contentApi));
        model.put("iurl", new ImageUrlCreator("http://localhost:8080/image", contentApi));
        model.put("contentApi", contentApi);

        if (article != null) {
            return "article";
        } else {
            return "section";
        }
    }

    private List<String> getFriendlyAliasPath(HttpServletRequest request) {
        String[] path = ((String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split("/");
        return Arrays.asList(Arrays.copyOfRange(path, 1, path.length));
    }

    class ContentUrlCreator {
        private ContentApi contentApi;

        public ContentUrlCreator(ContentApi contentApi) {
            this.contentApi = contentApi;
        }

        public String create(String id) {
            List<String> friendlyAliases = new ArrayList<>();
            id = "contentid/" + id;
            while (true) {
                Map<String, Object> content = contentApi.content(id);
                friendlyAliases.add(0, ContentMapUtil.getFriendlyAlias(content));
                String parentId = ContentMapUtil.getParentId(content);
                if (parentId.contains("all-sites")) {
                    return "/" + String.join("/", friendlyAliases);
                }
                id = parentId;
            }
        }
    }

    class ImageUrlCreator {
        private String baseUrl;
        private ContentApi contentApi;

        public ImageUrlCreator(String baseUrl, ContentApi contentApi) {
            this.baseUrl = baseUrl;
            this.contentApi = contentApi;
        }

        // Supports jpg in 2:1 format
        public String create(String id) {
            if (contentApi.isSymbolicId(id)) {
                id = contentApi.translateSymbolicId(id);
            }
            return baseUrl + "/" + id + "/image.jpg?a=2:1";
        }
    }
}
