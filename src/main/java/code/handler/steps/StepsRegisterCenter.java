package code.handler.steps;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class StepsRegisterCenter {

    private static Map<String, StepsHandler> stepsHandlerMap = new HashMap<>();

    private static volatile Map<String, StepsHandler> priorityMap = new ConcurrentHashMap<>();

    public static void register(String cmd, StepsHandler handler) {
        stepsHandlerMap.put(cmd, handler);
    }

    public static StepsHandler getRegister(String cmd) {
        return stepsHandlerMap.get(cmd);
    }
    public static Collection<StepsHandler> getRegisterList() {
        return stepsHandlerMap.values();
    }

    public synchronized static void priority(String chatId, String fromId, StepsHandler stepsHandler) {
        for (StepsHandler value : stepsHandlerMap.values()) {
            value.exit(chatId, fromId);
        }

        priorityMap.put(StepsSession.buildNewChatId(chatId, fromId), stepsHandler);
    }
    public static StepsHandler getPriority(String chatId, String fromId) {
        return priorityMap.get(StepsSession.buildNewChatId(chatId, fromId));
    }


}
