package code.handler.steps;

import code.handler.Command;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Slf4j
public class StepsHandler {

    private static volatile AtomicInteger IdAtomic = new AtomicInteger(1);

    private Command[] commands;

    private boolean debug;
    private StepErrorApi errorApi;
    private StepHandleApi initStep;
    private StepHandleApi[] stepHandleApis;

    private Map<String, ConcurrentHashMap<String, Object>> context = new ConcurrentHashMap<>();

    private Map<String, List<String>> message = new ConcurrentHashMap<>();

    private Map<String, Boolean> stepWorkStatus = new ConcurrentHashMap<>();
    private Map<String, Integer> stepId = new ConcurrentHashMap<>();

    private StepsHandler() {
    }

    public StepsHandler bindCommand(Command[] commands) {
        this.commands = commands;
        for (Command command : commands) {
            StepsRegisterCenter.register(command.getCmd(), this);
        }
        return this;
    }

    public static StepsHandler build(boolean debug, StepErrorApi errorApi, StepHandleApi step) {
        return build(debug, errorApi, null, step);
    }

    public static StepsHandler build(boolean debug, StepErrorApi errorApi, StepHandleApi initStep, StepHandleApi... steps) {
        StepsHandler handler = new StepsHandler();

        handler.debug = debug;
        handler.errorApi = errorApi;
        handler.initStep = initStep;
        handler.stepHandleApis = steps;

        return handler;
    }

    public boolean hasInit(String chatId, String fromId) {
        return stepId.containsKey(StepsSession.buildNewChatId(chatId, fromId));
    }
    public boolean isInit() {
        return null != initStep;
    }

    public void init(String chatId, String fromId, Integer replyToMessageId, String text) {
        StepsRegisterCenter.priority(chatId, fromId, this);

        Boolean stepsWorkStatusBool = stepWorkStatus.get(StepsSession.buildNewChatId(chatId, fromId));
        if (null != stepsWorkStatusBool && stepsWorkStatusBool) {
            return;
        }

        stepWorkStatus.put(StepsSession.buildNewChatId(chatId, fromId), true);

        StepResult execute = null;
        try {
            List<String> list = Collections.synchronizedList(new ArrayList<>());
            ConcurrentHashMap<String, Object> contextMap = new ConcurrentHashMap<>();
            if (null != initStep) {
                execute = initStep.execute(chatId, fromId, replyToMessageId, text, 0, list, contextMap);
            }
            if ((null != execute && execute.isOk()) || null == initStep) {
                context.remove(StepsSession.buildNewChatId(chatId, fromId));
                message.remove(StepsSession.buildNewChatId(chatId, fromId));
                stepId.remove(StepsSession.buildNewChatId(chatId, fromId));
                list.add(text);
                message.put(StepsSession.buildNewChatId(chatId, fromId), list);
                context.put(StepsSession.buildNewChatId(chatId, fromId), contextMap);
                stepId.put(StepsSession.buildNewChatId(chatId, fromId), IdAtomic.incrementAndGet());
                if (debug) {
                    log.info("Steps init, id: {}, chat id: {}, text: {}, list: {}", stepId.get(StepsSession.buildNewChatId(chatId, fromId)), chatId, text, JSON.toJSONString(list));
                }
            }
        } catch (Exception e) {
            errorApi.callback(e, chatId, fromId, replyToMessageId);
        } finally {
            stepWorkStatus.put(StepsSession.buildNewChatId(chatId, fromId), false);

            if (null != execute) {
                if (execute.isNext()) {
                    step(chatId, fromId, replyToMessageId, text);
                }
                if (execute.isEnd()) {
                    exit(chatId, fromId);
                }
            }
        }
    }

    public void next(String chatId, String fromId, Integer replyToMessageId, String text) {
        step(chatId, fromId, replyToMessageId, StringUtils.isBlank(text) ? "next" : text);
    }

    public StepExecuteResult step(String chatId, String fromId, Integer replyToMessageId, String text) {

        if (!hasInit(chatId, fromId) && !isInit()) {
            init(chatId, fromId, replyToMessageId, text);
        }

        Boolean stepsWorkStatusBool = stepWorkStatus.get(StepsSession.buildNewChatId(chatId, fromId));
        if (null != stepsWorkStatusBool && stepsWorkStatusBool) {
            return StepExecuteResult.work();
        }
        stepWorkStatus.put(StepsSession.buildNewChatId(chatId, fromId), true);

        StepResult execute = null;
        try {
            if (!message.containsKey(StepsSession.buildNewChatId(chatId, fromId))) {
                return StepExecuteResult.not();
            }

            List<String> list = message.get(StepsSession.buildNewChatId(chatId, fromId));
            Map<String, Object> contextMap = context.get(StepsSession.buildNewChatId(chatId, fromId));
            execute = this.stepHandleApis[list.size() - 1].execute(chatId, fromId, replyToMessageId, text, list.size(), list, contextMap);
            if (execute.isOk()) {
                list.add(text);
            }
            if (debug) {
                log.info("Step, id: {}, chat id: {}, text: {}, list: {}, context: {}", stepId.get(StepsSession.buildNewChatId(chatId, fromId)), chatId, text, JSON.toJSONString(list), JSON.toJSONString(contextMap));
            }
            if ((list.size() - 1) >= this.stepHandleApis.length) {
                if (debug) {
                    log.info("Step finish, id: {}, chat id: {}, text: {}, list: {}, context: {}", stepId.get(StepsSession.buildNewChatId(chatId, fromId)), chatId, text, JSON.toJSONString(list), JSON.toJSONString(contextMap));
                }
                exit(chatId, fromId);
            }
        } catch (Exception e) {
            errorApi.callback(e, chatId, fromId, replyToMessageId);
        } finally {
            stepWorkStatus.put(StepsSession.buildNewChatId(chatId, fromId), false);

            if (null != execute) {
                if (execute.isEnd()) {
                    exit(chatId, fromId);
                } else if (execute.isNext()) {
                    next(chatId, fromId, replyToMessageId, execute.getText());
                }
            }
        }
        return StepExecuteResult.ok(execute);
    }

    public void exit(String chatId, String fromId) {
        String newChatId = StepsSession.buildNewChatId(chatId, fromId);

        message.remove(newChatId);
        context.remove(newChatId);
        stepWorkStatus.remove(newChatId);
        stepId.remove(newChatId);
    }

}
