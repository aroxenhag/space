package space;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ContentMapUtil {
    public static String getString(Map<String, Object> map, String key) {
        Object o = getObject(map, key);
        return  (String) o;
    }

    public static Object getObject(Map<String, Object> map, String key) {
        List<String> keys = Arrays.asList(key.split("\\."));
        Map<String, Object> tmpMap = map;
        for (int i = 0; i < keys.size() - 1; i++) {
            String subKey = keys.get(i);
            tmpMap = (Map<String, Object>) tmpMap.get(subKey);
        }

        return tmpMap.get(keys.get(keys.size() - 1));
    }

    public static String getType(Map<String, Object> map) {
        return getString(map, "aspects.contentData.data._type");
    }

    public static String getParentId(Map<String, Object> map) {
        return getString(map, "aspects.contentData.data.parentId");
    }

    public static String getFriendlyAlias(Map<String, Object> map) {
        return getString(map, "aspects.contentData.data.friendlyAlias");
    }
}
