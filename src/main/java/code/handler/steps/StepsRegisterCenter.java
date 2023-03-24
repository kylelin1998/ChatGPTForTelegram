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

    public synchronized static void priority(StepsChatSession stepsChatSession, StepsHandler stepsHandler) {
        for (StepsHandler value : stepsHandlerMap.values()) {
            value.exit(stepsChatSession);
        }

        priorityMap.put(stepsChatSession.getSessionId(), stepsHandler);
    }
    public synchronized static void finish(StepsChatSession stepsChatSession) {
        priorityMap.remove(stepsChatSession.getSessionId());
    }
    public static StepsHandler getPriority(StepsChatSession stepsChatSession) {
        return priorityMap.get(stepsChatSession.getSessionId());
    }


}
