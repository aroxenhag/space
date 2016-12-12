package space;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContentEventListener {

    @Autowired
    private ApplicationEventPublisher publisher;

    @KafkaListener(topics = "polopoly.changelist")
    public void listen(String event) {
        String payload = (String) new Gson().fromJson(event, Map.class).get("payload");
        System.out.println(payload);
        try {
            ContentEvent contentEvent = new ContentEvent(payload);
            publisher.publishEvent(contentEvent);
        } catch (Exception e) {
            // TODO: Let's ignore unparseable payloads for now.
        }
    }

    class ContentEvent {

        private final String type;
        private final String id;
        private final long revision;
        private final boolean isDraft;

        ContentEvent(String payload) {
            String[] strings = payload.split(":");
            this.type = strings[0];
            String prefix = strings[1];
            String idAndRevision = strings[2];
            int revisionDelimiterIndex = idAndRevision.indexOf("/");
            if (revisionDelimiterIndex != -1) {
                revision = Long.parseLong(idAndRevision.substring(revisionDelimiterIndex + 1));
                this.id = "contentid/" + prefix + ":" + idAndRevision.substring(0, revisionDelimiterIndex);
            } else {
                this.id = "contentid/" + prefix + ":" + idAndRevision;
                revision = -1;
            }
            this.isDraft = prefix.equals("draft");
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public long getRevision() {
            return revision;
        }

        public boolean isDraft() {
            return isDraft;
        }

        @Override
        public String toString() {
            return "ContentEvent{" +
                    "type='" + type + '\'' +
                    ", id='" + id + '\'' +
                    ", revision=" + revision +
                    ", isDraft=" + isDraft +
                    '}';
        }
    }
}
