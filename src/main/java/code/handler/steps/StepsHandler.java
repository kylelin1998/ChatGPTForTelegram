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

    public boolean hasInit(StepsChatSession stepsChatSession) {
        return stepId.containsKey(stepsChatSession.getSessionId());
    }
    public boolean isInit() {
        return null != initStep;
    }

    public void init(StepsChatSession stepsChatSession) {
        StepsRegisterCenter.priority(stepsChatSession, this);
        String sessionId = stepsChatSession.getSessionId();

        Boolean stepsWorkStatusBool = stepWorkStatus.get(sessionId);
        if (null != stepsWorkStatusBool && stepsWorkStatusBool) {
            return;
        }

        stepWorkStatus.put(sessionId, true);

        StepResult execute = null;
        try {
            List<String> list = Collections.synchronizedList(new ArrayList<>());
            ConcurrentHashMap<String, Object> contextMap = new ConcurrentHashMap<>();
            if (null != initStep) {
                execute = initStep.execute(stepsChatSession, 0, list, contextMap);
            }
            if ((null != execute && execute.isOk()) || null == initStep) {
                context.remove(sessionId);
                message.remove(sessionId);
                stepId.remove(sessionId);
                list.add(stepsChatSession.getText());
                message.put(sessionId, list);
                context.put(sessionId, contextMap);
                stepId.put(sessionId, IdAtomic.incrementAndGet());
                if (debug) {
                    log.info("Steps init, id: {}, chat id: {}, text: {}, list: {}", stepId.get(sessionId), stepsChatSession.getChatId(), stepsChatSession.getText(), JSON.toJSONString(list));
                }
            }
        } catch (Exception e) {
            errorApi.callback(e, stepsChatSession);
        } finally {
            stepWorkStatus.put(sessionId, false);

            if (null != execute) {
                if (execute.isNext()) {
                    step(stepsChatSession);
                }
                if (execute.isEnd()) {
                    exit(stepsChatSession);
                }
            }
        }
    }

    public void next(StepsChatSession stepsChatSession) {
        step(stepsChatSession);
    }

    public StepExecuteResult step(StepsChatSession stepsChatSession) {
        String sessionId = stepsChatSession.getSessionId();
        if (!hasInit(stepsChatSession) && !isInit()) {
            init(stepsChatSession);
        }

        Boolean stepsWorkStatusBool = stepWorkStatus.get(sessionId);
        if (null != stepsWorkStatusBool && stepsWorkStatusBool) {
            return StepExecuteResult.work();
        }
        stepWorkStatus.put(sessionId, true);

        StepResult execute = null;
        try {
            if (!message.containsKey(sessionId)) {
                return StepExecuteResult.not();
            }

            List<String> list = message.get(sessionId);
            Map<String, Object> contextMap = context.get(sessionId);
            execute = this.stepHandleApis[list.size() - 1].execute(stepsChatSession, list.size(), list, contextMap);
            if (execute.isOk()) {
                list.add(stepsChatSession.getText());
            }
            if (debug) {
                log.info("Step, id: {}, chat id: {}, text: {}, list: {}, context: {}", stepId.get(sessionId), stepsChatSession.getChatId(), stepsChatSession.getText(), JSON.toJSONString(list), JSON.toJSONString(contextMap));
            }
            if ((list.size() - 1) >= this.stepHandleApis.length) {
                if (debug) {
                    log.info("Step finish, id: {}, chat id: {}, text: {}, list: {}, context: {}", stepId.get(sessionId), stepsChatSession.getChatId(), stepsChatSession.getText(), JSON.toJSONString(list), JSON.toJSONString(contextMap));
                }
                exit(stepsChatSession);
            }
        } catch (Exception e) {
            errorApi.callback(e, stepsChatSession);
        } finally {
            stepWorkStatus.put(sessionId, false);

            if (null != execute) {
                if (execute.isEnd()) {
                    exit(stepsChatSession);
                } else if (execute.isNext()) {
                    next(
                            StepsChatSessionBuilder.clone(stepsChatSession).setText(execute.getText()).build()
                    );
                }
            }
        }
        return StepExecuteResult.ok(execute);
    }

    public void exit(StepsChatSession stepsChatSession) {
        String sessionId = stepsChatSession.getSessionId();

        message.remove(sessionId);
        context.remove(sessionId);
        stepWorkStatus.remove(sessionId);
        stepId.remove(sessionId);
        StepsRegisterCenter.finish(stepsChatSession);
    }

}
