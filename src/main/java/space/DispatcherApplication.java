package space;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SpringBootApplication
@EnableCaching
public class DispatcherApplication {

    static String contentApiBaseUrl;

    @Value("${content-api-base-url}")
    public void setContentApiBaseUrl(String baseUrl) {
        contentApiBaseUrl = baseUrl;
    }

    public static String AUTH_TOKEN;

    private final static Logger LOG = Logger.getLogger(DispatcherApplication.class.getName());

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(DispatcherApplication.class, args);
        aquireAndSetToken();
    }

    private static void aquireAndSetToken() {
        try {
            String url = contentApiBaseUrl + "/security/token";
            String json = "{\"username\": \"root\",\"password\": \"god\"}";

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
}
