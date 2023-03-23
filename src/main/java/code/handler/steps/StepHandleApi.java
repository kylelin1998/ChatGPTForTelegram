package code.handler.steps;

import java.util.List;
import java.util.Map;

public interface StepHandleApi {

    StepResult execute(StepsChatSession stepsChatSession, int index, List<String> list, Map<String, Object> context);

}
