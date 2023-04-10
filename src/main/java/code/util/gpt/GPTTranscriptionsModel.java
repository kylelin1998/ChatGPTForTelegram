package code.util.gpt;

import lombok.Getter;

@Getter
public enum GPTTranscriptionsModel {

    Whisper_1("whisper-1"),

    ;

    private String model;

    GPTTranscriptionsModel(String model) {
        this.model = model;
    }

}
