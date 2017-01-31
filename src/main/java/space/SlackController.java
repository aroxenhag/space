package space;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

@Controller
public class SlackController {

    private final static Logger LOG = Logger.getLogger(SlackController.class.getName());

    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();

    @Value("${site.publicBaseUrl}")
    String baseUrl;

    @Value("${site.publicImageBaseUrl}")
    private String imageServiceBaseUrl;

    @Autowired
    private ContentApi contentApi;

    @RequestMapping(path = "/slack", produces = "application/json")
    @ResponseBody
    public String trigger() throws IOException {

        String fallback = "Required plain-text summary of the attachment.";
        String color = "#36a64f";
        String preText = "Optional text that appears above the attachment block";
        String title = "Astronaut Buzz Aldrin evacuated from South Pole";
        String titleLink = "http://www.atex.com";
        String text = "US astronaut Buzz Aldrin, the second man to walk on the Moon, has been evacuated from the South Pole after falling ill, the BBC is reporting.";
        String section = "News";

        String footer = "The Localhost";
        String footerIcon = baseUrl + "/static/images/the-localhost-512.png";
        long timestamp = new Date().getTime();

        String image = "http://cached-images.bonnier.news/cms30/UploadedImages/2016/10/12/5290d755-ef51-4d59-bb92-d8d186533024/bigOriginal.jpg?interpolation=lanczos-none&downsize=*:549&output-quality=80&output-format=jpeg";

        String author = "Edmund Green";
        String authorIcon = "https://images-1.svd.se/v2/images/author/ec26d60f-5d34-4c76-9a93-89516c374c9c?q=60&tight=true&w=200&s=2256d2ab7ec6994e0c288a0f84333357d44a043a";

        postMessage(fallback, color, preText, author, authorIcon, title, titleLink, text, section, image, footer, footerIcon, timestamp);

        return "{\"message\": \"ok\"}";
    }

    private void postMessage(String fallback, String color, String preText, String author, String authorIcon, String title, String titleLink, String text, String section, String image, String footer, String footerIcon, long timestamp) throws IOException {
        JsonObject attachment = new JsonObject();
        attachment.addProperty("fallback", fallback);
        attachment.addProperty("color", color);
        attachment.addProperty("pretext", preText);
        if (StringUtils.isNotEmpty(author)) {
            attachment.addProperty("author_name", author);
        }
        if (StringUtils.isNotEmpty(authorIcon)) {
            attachment.addProperty("author_icon", authorIcon);
        }
        attachment.addProperty("title", title);
        attachment.addProperty("title_link", titleLink);
        if (StringUtils.isNotEmpty(text)) {
            attachment.addProperty("text", text);
        }
        if (StringUtils.isNotEmpty(image)) {
            attachment.addProperty("image_url", image);
        }
        if (StringUtils.isNotEmpty(footer)) {
            attachment.addProperty("footer", footer);
        }
        if (StringUtils.isNotEmpty(footerIcon)) {
            attachment.addProperty("footer_icon", footerIcon);
        }
        attachment.addProperty("ts", timestamp);

        JsonObject sectionField = new JsonObject();
        sectionField.addProperty("title", "Section");
        sectionField.addProperty("value", section);
        sectionField.addProperty("short", false);
        JsonArray fields = new JsonArray();
        fields.add(sectionField);
        attachment.add("fields", fields);

        JsonArray attachments = new JsonArray();
        attachments.add(attachment);

        JsonObject message = new JsonObject();
        message.add("attachments", attachments);

        message.addProperty("username", "new-content-bot");

        RequestBody body = RequestBody.create(JSON, message.toString());

        Request request = new Request.Builder()
                .url("https://hooks.slack.com/services/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")
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
    }

    @EventListener(condition = "#event.draft == false and #event.type == 'mutation'")
    public void update(ContentEventListener.ContentEvent event) {
        String contentId = event.getId();
        try {
            Map<String, Object> content = contentApi.getContent(contentId);
            String type = ContentMapUtil.getType(content);
            if ("web-article".equals(type)) {
                String title = ContentMapUtil.getTitle(content);
                if (StringUtils.isEmpty(title)) {
                    title = "[Unnamed article]";
                }
                String lead = ContentMapUtil.getString(content, "aspects.contentData.data.lead");
                DispatcherController.ContentUrlCreator urlCreator = new DispatcherController.ContentUrlCreator(contentApi);
                String url = baseUrl + urlCreator.create(contentId.substring("contentid/".length()));

                String color = "#36a64f";
                String preText = "New article published";
                Map<String, Object> section = contentApi.getContent(ContentMapUtil.getParentId(content));
                String sectionName = ContentMapUtil.getTitle(section);

                String author = ContentMapUtil.getString(content, "aspects.contentData.data.byline");

                String footer = "The Localhost";
                String footerIcon = baseUrl + "/static/images/the-localhost-512.png";
                long timestamp = new Date().getTime();

                String image = null;
                String imageRef = ContentMapUtil.getString(content, "aspects.contentData.data.imageRef");
                System.out.println(imageRef);
                if (StringUtils.isNotEmpty(imageRef)) {
                    DispatcherController.ImageUrlCreator imageUrlCreator = new DispatcherController.ImageUrlCreator(contentApi, imageServiceBaseUrl);
                    image = imageUrlCreator.create(imageRef);
                    System.out.println(image);
                }

                //String fallback, String color, String preText, String author, String authorIcon, String title, String titleLink, String text, String section, String image, String footer, String footerIcon, long timestamp
                postMessage(title, color, preText, author, null, title, url, lead, sectionName, image, footer, footerIcon, timestamp);

            }
        } catch (Exception e) {
            // If we cannot get the content, we probably do not care
            LOG.warning("Error getting content for event: " + event);
        }
    }
}
