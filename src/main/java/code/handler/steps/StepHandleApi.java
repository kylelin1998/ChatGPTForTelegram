package code.handler.steps;

import java.util.List;
import java.util.Map;

public interface StepHandleApi {

    StepResult execute(String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context);

}
