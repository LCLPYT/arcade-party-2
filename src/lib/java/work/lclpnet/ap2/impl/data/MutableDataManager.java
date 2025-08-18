package work.lclpnet.ap2.impl.data;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.data.DynamicData;

public class MutableDataManager implements DataManager {

    private DynamicData data = null;

    @NotNull
    public String string(String str) {
        // check if str is a reference
        if (data != null && !str.isEmpty() && str.charAt(0) == '@') {
            String key = str.substring(1);

            if (data.get(key) instanceof String dataStr) {
                return dataStr;
            }
        }

        return str;
    }

    public void setData(DynamicData data) {
        this.data = data;
    }
}
