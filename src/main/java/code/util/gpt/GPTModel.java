package code.util.gpt;

import lombok.Getter;

@Getter
public enum GPTModel {

    Gpt3_5Turbo("gpt-3.5-turbo"),

    ;

    private String model;

    GPTModel(String model) {
        this.model = model;
    }

}
