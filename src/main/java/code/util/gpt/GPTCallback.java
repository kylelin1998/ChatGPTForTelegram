package code.util.gpt;

import code.util.gpt.response.GPTCallbackChatContent;

public interface GPTCallback {

    void call(GPTCallbackChatContent content);

}
