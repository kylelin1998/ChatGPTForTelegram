package code.util.gpt.response;

import lombok.Data;

import java.util.List;

@Data
public class GPTCallbackChatContent {

    private boolean done;
    private String content;

    private String id;

    private String object;
    private Long created;
    private String model;
    private List<GPTChatContentChoices> choices;

}
