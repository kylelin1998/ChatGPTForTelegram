package code.handler.steps;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StepExecuteResult {

    private boolean init;
    private StepResult stepResult;
    private boolean isWork;

    public static StepExecuteResult not() {
        return new StepExecuteResult(false, null, false);
    }

    public static StepExecuteResult work() {
        return new StepExecuteResult(true, null, true);
    }

    public static StepExecuteResult ok(StepResult stepResult) {
        return new StepExecuteResult(true, stepResult, true);
    }

}
