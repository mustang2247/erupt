package xyz.erupt.annotation.fun;

import java.util.Map;

/**
 * Created by liyuepeng on 2019-07-25.
 */
public interface FetchChoiceMapHandler {

    Map<String, String> fetch(String[] params);
}
