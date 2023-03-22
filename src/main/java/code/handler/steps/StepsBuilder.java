package code.handler.steps;

import code.handler.Command;

public class StepsBuilder {

    private Command[] commands;
    private boolean debug = true;
    private StepErrorApi errorApi;
    private StepHandleApi initStep;
    private StepHandleApi[] steps;

    private StepsBuilder() {}

    public static StepsBuilder create() {
        return new StepsBuilder();
    }

    public StepsBuilder bindCommand(Command... commands) {
        this.commands = commands;
        return this;
    }
    public StepsBuilder debug(boolean debug) {
        this.debug = debug;
        return this;
    }
    public StepsBuilder error(StepErrorApi errorApi) {
        this.errorApi = errorApi;
        return this;
    }
    public StepsBuilder init(StepHandleApi initStep) {
        this.initStep = initStep;
        return this;
    }
    public StepsBuilder steps(StepHandleApi... steps) {
        this.steps = steps;
        return this;
    }

    public StepsHandler build() {
        return StepsHandler.build(debug, errorApi, initStep, steps).bindCommand(commands);
    }

}
