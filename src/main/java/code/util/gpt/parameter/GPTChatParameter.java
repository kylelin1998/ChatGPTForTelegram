package code.util.gpt.parameter;

import lombok.Data;

import java.util.List;

@Data
public class GPTChatParameter {

    private String model;
    private boolean stream;
    private List<GPTMessage> messages;
    private String user;

}
