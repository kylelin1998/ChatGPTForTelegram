package code.handler.steps;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StepResult {

    private boolean ok;

    private boolean next;
    private String text;

    private boolean end;

    public static StepResult ok() {
        return new StepResult(true, false, null, false);
    }

    public static StepResult reject() {
        return new StepResult(false, false, null, false);
    }

    public static StepResult next() {
        return new StepResult(true, true, null, false);
    }

    public static StepResult next(String text) {
        return new StepResult(true, true, text, false);
    }

    public static StepResult end() {
        return new StepResult(true, false, null, true);
    }

}
