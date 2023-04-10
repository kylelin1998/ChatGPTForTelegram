package code.util.gpt.parameter;

import lombok.Data;

import java.io.File;

@Data
public class GPTTranscriptionsParameter {

    private File file;

    private String model;

}
