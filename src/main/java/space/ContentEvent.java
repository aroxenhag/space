package space;

public class ContentEvent {

    private String type;
    private String id;
    private long revision;
    private boolean isDraft;

    private ContentEvent() {
    }

    public static ContentEvent fromMessage(String payload) {

        String[] strings = payload.split(":");

        String eventType = strings[0];

        // Only care about mutation events for now
        if ("mutation".equals(eventType)) {
            ContentEvent contentEvent = new ContentEvent();

            contentEvent.type = eventType;

            // NOTE: Is mutation event - treat as mutation event
            String prefix = strings[1];
            contentEvent.isDraft = "draft".equals(prefix);

            String idAndRevision = strings[2];

            contentEvent.id = idAndRevision.substring(idAndRevision.indexOf("contentid/"), idAndRevision.lastIndexOf("/"));
            contentEvent.revision = Long.parseLong(idAndRevision.substring(idAndRevision.lastIndexOf("/") + 1));

            return contentEvent;
        } else {
            return null;
        }
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
