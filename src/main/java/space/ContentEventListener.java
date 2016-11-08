package space;

import kafka.api.FetchRequest;
import kafka.api.FetchRequestBuilder;
import kafka.api.PartitionOffsetRequestInfo;
import kafka.common.ErrorMapping;
import kafka.common.TopicAndPartition;
import kafka.javaapi.FetchResponse;
import kafka.javaapi.OffsetResponse;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.message.MessageAndOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContentEventListener {

    @Value("${kafka.host}")
    private String host;

    @Value("${kafka.port}")
    private int port;

    @Value("${kafka.topic}")
    private String topic;

    @Value("${kafka.partition}")
    private int partition;

    @Autowired
    private ContentApi contentApi;

    @Autowired
    private SimpMessagingTemplate template;

    private String leadBroker;
    private SimpleConsumer consumer;
    private long readOffset;
    private String clientName;

    @PostConstruct
    public void init() {
        PartitionMetadata metadata = findLeader(host, port, topic, partition);
        if (metadata == null) {
            System.out.println("Can't find metadata for Topic and Partition. Exiting");
            return;
        }
        if (metadata.leader() == null) {
            System.out.println("Can't find Leader for Topic and Partition. Exiting");
            return;
        }
        leadBroker = metadata.leader().host();
        clientName = "Client_" + topic + "_" + partition;

        consumer = new SimpleConsumer(leadBroker, port, 100000, 64 * 1024, clientName);
        readOffset = getLastOffset(consumer, topic, partition, kafka.api.OffsetRequest.LatestTime(), clientName);
    }

    @Scheduled(fixedDelay = 500)
    public void run() {
        try {
            int numErrors = 0;
            while (true) {
                FetchRequest req = new FetchRequestBuilder()
                        .clientId(clientName)
                        .addFetch(topic, partition, readOffset, 100000) // Note: this fetchSize of 100000 might need to be increased if large batches are written to Kafka
                        .build();
                FetchResponse fetchResponse = consumer.fetch(req);

                if (fetchResponse.hasError()) {
                    numErrors++;
                    // Something went wrong!
                    short code = fetchResponse.errorCode(topic, partition);
                    System.out.println("Error fetching data from the Broker:" + leadBroker + " Reason: " + code);
                    if (numErrors > 5) {
                        return;
                    }
                    if (code == ErrorMapping.OffsetOutOfRangeCode()) {
                        // We asked for an invalid offset. For simple case ask for the last element to reset
                        readOffset = getLastOffset(consumer, topic, partition, kafka.api.OffsetRequest.LatestTime(), clientName);
                        return;
                    }
                    consumer.close();
                    consumer = null;
                    leadBroker = findNewLeader(leadBroker, topic, partition, port);
                    return;
                }
                numErrors = 0;

                long numRead = 0;
                for (MessageAndOffset messageAndOffset : fetchResponse.messageSet(topic, partition)) {
                    long currentOffset = messageAndOffset.offset();
                    if (currentOffset < readOffset) {
                        System.out.println("Found an old offset: " + currentOffset + " Expecting: " + readOffset);
                        continue;
                    }
                    readOffset = messageAndOffset.nextOffset();
                    ByteBuffer payload = messageAndOffset.message().payload();

                    byte[] bytes = new byte[payload.limit()];
                    payload.get(bytes);

                    String message = new String(bytes, "UTF-8");

                    if (message.startsWith("mutation:") && !message.contains("draft")) {

                        String contentId = "contentid/" + message.substring(message.indexOf(":") + 1);
                        String unversionedContentId = contentApi.unversioned(contentId);
                        System.out.println("evicting from unversioned id cache: " + unversionedContentId + " (full id: " + contentId + ")");
                        contentApi.evict(unversionedContentId);

                        boolean liveUpdatesEnabled = false;
                        if (liveUpdatesEnabled) {
                            Map<String, Object> content = contentApi.getContent(contentId);
                            String type = ContentMapUtil.getType(content);
                            if ("web-article".equals(type)) {
                                String title = ContentMapUtil.getTitle(content);
                                String lead = ContentMapUtil.getString(content, "aspects.contentData.data.lead");
                                if (title != null && title.toUpperCase().startsWith("EXTRA:")) {
                                    DispatcherController.ContentUrlCreator urlCreator = new DispatcherController.ContentUrlCreator(contentApi);
                                    String url = urlCreator.create(unversionedContentId.substring("contentid/".length()));
                                    BreakingNewsController.BreakingArticle breakingArticle = new BreakingNewsController.BreakingArticle(title, lead, url);
                                    template.convertAndSend("/topic/breaking", breakingArticle);
                                }
                            }
                        }

                    }

                    numRead++;
                }

                if (numRead == 0) {
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static long getLastOffset(SimpleConsumer consumer, String topic, int partition,
                                     long whichTime, String clientName) {
        TopicAndPartition topicAndPartition = new TopicAndPartition(topic, partition);
        Map<TopicAndPartition, PartitionOffsetRequestInfo> requestInfo = new HashMap<TopicAndPartition, PartitionOffsetRequestInfo>();
        requestInfo.put(topicAndPartition, new PartitionOffsetRequestInfo(whichTime, 1));
        kafka.javaapi.OffsetRequest request = new kafka.javaapi.OffsetRequest(
                requestInfo, kafka.api.OffsetRequest.CurrentVersion(), clientName);
        OffsetResponse response = consumer.getOffsetsBefore(request);

        if (response.hasError()) {
            System.out.println("Error fetching data Offset Data the Broker. Reason: " + response.errorCode(topic, partition));
            return 0;
        }
        long[] offsets = response.offsets(topic, partition);
        return offsets[0];
    }

    private String findNewLeader(String a_oldLeader, String a_topic, int a_partition, int a_port) throws Exception {
        for (int i = 0; i < 3; i++) {
            boolean goToSleep = false;
            PartitionMetadata metadata = findLeader(host, a_port, a_topic, a_partition);
            if (metadata == null) {
                goToSleep = true;
            } else if (metadata.leader() == null) {
                goToSleep = true;
            } else if (a_oldLeader.equalsIgnoreCase(metadata.leader().host()) && i == 0) {
                // first time through if the leader hasn't changed give ZooKeeper a second to recover
                // second time, assume the broker did recover before failover, or it was a non-Broker issue
                //
                goToSleep = true;
            } else {
                return metadata.leader().host();
            }
            if (goToSleep) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
            }
        }
        System.out.println("Unable to find new leader after Broker failure. Exiting");
        throw new Exception("Unable to find new leader after Broker failure. Exiting");
    }

    private PartitionMetadata findLeader(String host, int a_port, String a_topic, int a_partition) {
        PartitionMetadata returnMetaData = null;
        SimpleConsumer consumer = null;
        try {
            consumer = new SimpleConsumer(host, a_port, 100000, 64 * 1024, "leaderLookup");
            List<String> topics = Collections.singletonList(a_topic);
            TopicMetadataRequest req = new TopicMetadataRequest(topics);
            kafka.javaapi.TopicMetadataResponse resp = consumer.send(req);

            List<TopicMetadata> metaData = resp.topicsMetadata();
            loop:
            for (TopicMetadata item : metaData) {
                for (PartitionMetadata part : item.partitionsMetadata()) {
                    if (part.partitionId() == a_partition) {
                        returnMetaData = part;
                        break loop;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error communicating with Broker [" + host + "] to find Leader for [" + a_topic
                    + ", " + a_partition + "] Reason: " + e);
        } finally {
            if (consumer != null) consumer.close();
        }
        return returnMetaData;
    }
}