package space;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@EnableFeignClients
public class DispatcherController {

    @Autowired
    private ContentApi contentApi;

    @Value("${image-service-base-url}")
    private String imageServiceBaseUrl;

    @Value("${default-site}")
    private String defaultSite;

    @RequestMapping(value = "/")
    public String root() {
        return "redirect:/" + defaultSite;
    }

    @RequestMapping(value = "/{site}/about/{tag}")
    public String about(@PathVariable("site") String site, @PathVariable("tag") String tag, HttpServletRequest request, HttpServletResponse response, Map<String, Object> model) {
        Map<String, Object> result = contentApi.search("tags_ss:" + tag);
        List<Map<String, Object>> articles = getArticlesForResult(result);
        model.put("metaEntity", tag);
        return dispatch(response, model, Arrays.asList("friendly/" + site), articles);
    }

    @RequestMapping(value = "/{site}/by/{author}")
    public String by(@PathVariable("site") String site, @PathVariable("author") String author, HttpServletRequest request, HttpServletResponse response, Map<String, Object> model) {
        Map<String, Object> result = contentApi.search("byline_s:" + author);
        List<Map<String, Object>> articles = getArticlesForResult(result);
        model.put("metaEntity", author);
        return dispatch(response, model, Arrays.asList("friendly/" + site), articles);
    }

    @RequestMapping(value = "{path:(?!websocket|webjars|wro4j|static|error).*$}/**")
    public String dispatch(HttpServletRequest request, HttpServletResponse response, Map<String, Object> model) {

        // Get content path
        List<String> friendlyAliasPath = getFriendlyAliasPath(request);
        List<String> contentPath = new ArrayList<>();
        friendlyAliasPath.forEach(s -> {
            contentPath.add("friendly/" + s);
        });

        return dispatch(response, model, contentPath, null);
    }

    private String dispatch(HttpServletResponse response, Map<String, Object> model, List<String> contentPath, List<Map<String, Object>> articles) {
        response.addHeader("Cache-Control", "max-age=5");


        // Get contents for content path
        List<Map<String, Object>> contents = contentApi.batch(contentPath);
        model.put("pathContents", contents);
        List<String> pathIds = new ArrayList<>();
        for (Map<String, Object> content : contents) {
            pathIds.add(ContentMapUtil.getId(content));
        }
        model.put("pathIds", pathIds);


        // Get site and section from contents in content path
        Map<String, Object> site = contents.get(0);
        Map<String, Object> section = site;
        Map<String, Object> article = null;
        for (Map content : contents) {
            String type = ContentMapUtil.getType(content);
            if ("web-section".equals(type)) {
                section = content;
            } else if ("web-article".equals(type)) {
                article = content;
            }
        }
        model.put("site", site);
        model.put("section", section);
        model.put("article", article);
        model.put("ga_tracker", createTracker(site, section, article));

        // Get all top level sections for navigation
        List sectionIds = (List) ContentMapUtil.getObject(site, "aspects.contentData.data.sections");
        List<Map<String, Object>> sections = new ArrayList<>();
        if (sectionIds != null) {
            sectionIds.forEach(sectionId -> {
                sections.add(contentApi.content((String) sectionId));
            });
        }
        model.put("sections", sections);


        // Get top articles for site for top list
        List<String> siteSubSectionIds = new ArrayList<>();
        addAllSubSectionIds(contentApi, site, siteSubSectionIds);
        Map siteArticlesResult = contentApi.search("type:article AND parentId_s:(" + String.join(" ", siteSubSectionIds) + ")");
        List<Map<String, Object>> siteTopArticles = getArticlesForResult(siteArticlesResult);
        model.put("topArticles", siteTopArticles);


        // Get most recent articles for current section (and its subsections)
        // NOTE: Since the parent ids are the uuids that are never resolved to real ids,
        // we have to use the uuids here.
        List<Map<String, Object>> pageArticles = articles; // Get from param. If null, use most recent articles from section
        if (pageArticles == null) {
            List<String> currentSectionSubSectionIds = new ArrayList<>();
            addAllSubSectionIds(contentApi, section, currentSectionSubSectionIds);
            Map currentSectionArticlesResult = contentApi.search("type:article AND parentId_s:(" + String.join(" ", currentSectionSubSectionIds) + ")");
            pageArticles = getArticlesForResult(currentSectionArticlesResult);
        }


        // Build layout
        String layoutConfigStr = ContentMapUtil.getString(section, "aspects.contentData.data.layoutConfig");
        if (StringUtils.isEmpty((layoutConfigStr))) {
            layoutConfigStr = "1, 3, 1, 2, 3*, 4, 3, 1, 2, 3, 4";
        }
        List<String> layoutConfig = Arrays.asList(layoutConfigStr.split(","));
        String adsConfigStr = ContentMapUtil.getString(section, "aspects.contentData.data.adsConfig");
        if (StringUtils.isEmpty(adsConfigStr)) {
            adsConfigStr = "2, 4, 7, 8, 9, 12, 15";
        }
        Set<Integer> adsConfig = new HashSet<>();
        for (String adPosition : adsConfigStr.split(",")) {
            adsConfig.add(Integer.parseInt(adPosition.trim()));
        }
        int articleIndex = 0;
        int rowIndex = 1;
        List<Map<String, Object>> rows = new ArrayList<>();
        outer:
        for (String rowConfigStr : layoutConfig) {
            if (adsConfig.contains(rowIndex)) {
                Map<String, Object> rowConfig = new HashMap<>();
                rowConfig.put("type", "ad");
                rows.add(rowConfig);
            }
            rowConfigStr = rowConfigStr.trim();

            Map<String, Object> rowConfig = new HashMap<>();
            int articleCount = -1;
            if (rowConfigStr.endsWith("*")) {
                articleCount = Integer.parseInt(rowConfigStr.substring(0, rowConfigStr.length() - 1));
                rowConfig.put("type", "carousel");
            } else {
                articleCount = Integer.parseInt(rowConfigStr);
                rowConfig.put("type", "cols");
            }
            List<Map<String, Object>> rowContent = new ArrayList<>();
            for (int i = 0; i < articleCount; i++) {
                if (articleIndex >= pageArticles.size()) {
                    break outer;
                }
                rowContent.add(pageArticles.get(articleIndex++));
            }
            rowConfig.put("content", rowContent);
            rows.add(rowConfig);
            rowIndex++;
        }
        model.put("rows", rows);


        // Add generic utilities
        model.put("curl", new ContentUrlCreator(contentApi));
        model.put("lpage", new LandingPageUrlCreator());
        model.put("iurl", new ImageUrlCreator(contentApi));
        model.put("contentApi", contentApi);
        model.put("utils", new Utils());
        model.put("date", new DateUtil());


        // Dispatch to correct template based on type
        if (article != null) {
            return "article";
        } else {
            return "section";
        }
    }

    private Map<String, String> createTracker(Map<String, Object> site, Map<String, Object> section, Map<String, Object> article) {
        Map<String, String> tracker = new HashMap<>();
        tracker.put("dimension1", (site != null) ? ContentMapUtil.getFriendlyAlias(site) : "");
        tracker.put("dimension2", (section != null) ? ContentMapUtil.getFriendlyAlias(section) : "");
        tracker.put("dimension3", (article != null) ? ContentMapUtil.getFriendlyAlias(article) : "");
        return tracker;
    }

    private List<Map<String, Object>> getArticlesForResult(Map currentSectionArticlesResult) {
        List docs = (List) ContentMapUtil.getObject(currentSectionArticlesResult, "response.docs");
        List<Map<String, Object>> articles = new ArrayList<>();
        docs.forEach(doc -> {
            String articleId = ContentMapUtil.getString((Map<String, Object>) doc, "id");
            articles.add(contentApi.content("contentid/" + articleId));
        });
        return articles;
    }

    private void addAllSubSectionIds(ContentApi contentApi, Map<String, Object> section, List<String> allSectionIds) {
        allSectionIds.add(ContentMapUtil.getUUID(section));
        allSectionIds.add("contentid/" + ContentMapUtil.getId(section).replaceAll(":", "\\\\:"));
        List<String> subSectionIds = (List) ContentMapUtil.getObject(section, "aspects.contentData.data.sections");
        if (subSectionIds != null) {
            for (String subSectionId : subSectionIds) {
                Map<String, Object> content = contentApi.content(subSectionId);
                addAllSubSectionIds(contentApi, content, allSectionIds);
            }
        }
    }

    private List<String> getFriendlyAliasPath(HttpServletRequest request) {
        String[] path = ((String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).split("/");
        return Arrays.asList(Arrays.copyOfRange(path, 1, path.length));
    }

    private String toFriendlyFormat(String string) {
        return StringUtils.stripStart(StringUtils.stripEnd(Normalizer.normalize(string.trim().toLowerCase(),
                Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^\\p{Alnum}]+", "-"), "-"), "-");
    }

    class DateUtil {
        public String time(Long time) {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mma");
            return sdf.format(new Date(time));
        }

        public String full(Long time) {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy • hh:mma");
            return sdf.format(new Date(time));
        }

        public String medium(Long time) {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy, h:mma");
            return sdf.format(new Date(time));
        }

        public String iso8601(Long time) {
            Calendar calendar = GregorianCalendar.getInstance();
            calendar.setTimeInMillis(time);
            return javax.xml.bind.DatatypeConverter.printDateTime(calendar);
        }
    }

    static class Utils {
        public boolean isEmpty(Object o, String key) {
            return isEmpty(ContentMapUtil.getObject((Map<String, Object>) o, key));
        }

        public boolean isEmpty(Object o) {
            return o == null || (o instanceof Collection && ((Collection) o).isEmpty());
        }
    }

    static class ContentUrlCreator {
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

    class LandingPageUrlCreator {
        public String create(Map<String, Object> site, String dimension, String entity) {
            String friendlyAlias = ContentMapUtil.getFriendlyAlias(site);
            return "/" + friendlyAlias + "/" + dimension + "/" + toFriendlyFormat(entity);
        }
    }

    class ImageUrlCreator {
        private ContentApi contentApi;

        public ImageUrlCreator(ContentApi contentApi) {
            this.contentApi = contentApi;
        }

        // Supports jpg in 2:1 format
        public String create(String id) {
            if (contentApi.isSymbolicId(id)) {
                id = contentApi.translateSymbolicId(id);
            }
            return imageServiceBaseUrl + "/" + id + "/image.jpg?f=2x1";
        }
    }
}
