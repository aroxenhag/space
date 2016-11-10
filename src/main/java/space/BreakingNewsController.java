package space;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class BreakingNewsController {

    @SendTo("/topic/breaking")
    public BreakingArticle greeting(BreakingArticle article) throws Exception {
        return article;
    }

    static class BreakingArticle {
        private String title;
        private String lead;
        private String url;

        public BreakingArticle(String title, String lead, String url) {
            this.title = title;
            this.lead = lead;
            this.url = url;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getLead() {
            return lead;
        }

        public void setLead(String lead) {
            this.lead = lead;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @Autowired
    private ContentApi contentApi;

    @Autowired
    private SimpMessagingTemplate template;

//    @EventListener(condition = "#event.draft == false and #event.type == 'mutation'")
    public void update(ContentEventListener.ContentEvent event) {
        String contentId = event.getId();
        Map<String, Object> content = contentApi.getContent(contentId);
        String type = ContentMapUtil.getType(content);
        if ("web-article".equals(type)) {
            String title = ContentMapUtil.getTitle(content);
            String lead = ContentMapUtil.getString(content, "aspects.contentData.data.lead");
            if (title != null && title.toUpperCase().startsWith("EXTRA:")) {
                DispatcherController.ContentUrlCreator urlCreator = new DispatcherController.ContentUrlCreator(contentApi);
                String url = urlCreator.create(contentId.substring("contentid/".length()));
                BreakingNewsController.BreakingArticle breakingArticle = new BreakingNewsController.BreakingArticle(title, lead, url);
                template.convertAndSend("/topic/breaking", breakingArticle);
            }
        }
    }
}





