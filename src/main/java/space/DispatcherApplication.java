package space;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SpringBootApplication
@EnableCaching
@EnableKafka
public class DispatcherApplication {

    static String contentApiBaseUrl;

    @Value("${content-api-base-url}")
    public void setContentApiBaseUrl(String baseUrl) {
        contentApiBaseUrl = baseUrl;
    }

    public static String AUTH_TOKEN;

    private final static Logger LOG = Logger.getLogger(DispatcherApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication.run(DispatcherApplication.class, args);
    }

    @Scheduled(fixedDelay = 1000 * 60 * 5)
    void aquireAndSetToken() {
        try {
            String url = contentApiBaseUrl + "/security/token";
            String json = "{\"username\": \"admin\",\"password\": \"123456\"}";

            HttpClient httpClient = HttpClientBuilder.create().build();

            HttpPost request = new HttpPost(url);
            StringEntity params = new StringEntity(json);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");

            Map<String, String> map = new Gson().fromJson(responseString, Map.class);
            String token = map.get("token");
            String userId = map.get("userId");
            Date expireTime = new Date(Long.parseLong(map.get("expireTime")));
            DispatcherApplication.AUTH_TOKEN = token;

            LOG.warning("Logged in to content api as: " + userId + ". Token will expire at " + expireTime);

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error aquiring authentication token. No content will be available.", e);
        }
    }

    @Bean
    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<Integer, String>>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Integer, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setPollTimeout(3000);
        return factory;
    }

    @Bean
    public ConsumerFactory<Integer, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    @Value("${kafka.host}")
    private String kafkaHost;

    @Value("${kafka.port}")
    private int kafkaPort;

    @Value("${kafka.groupId}")
    private String kafkaGroupId;

    @Bean
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHost + ":" + kafkaPort);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
