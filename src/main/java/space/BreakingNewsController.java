package space;

import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

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
}