package code.util.gpt.response;

import lombok.Data;

@Data
public class GPTChatContentChoices {

    private GPTChatContentChoicesDelta delta;

    private Integer index;
    private String finishReason;

}
