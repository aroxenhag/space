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
//        System.out.println(payload);
        try {
            ContentEvent contentEvent = ContentEvent.fromMessage(payload);
            if (contentEvent != null) {
                System.out.println(contentEvent);
                publisher.publishEvent(contentEvent);
            }
        } catch (Exception e) {
            // TODO: Let's ignore unparseable payloads for now.
        }
    }
}
