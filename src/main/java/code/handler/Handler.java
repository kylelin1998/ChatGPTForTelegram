package code.handler;

import code.config.*;
import code.eneity.RecordTableEntity;
import code.handler.commands.AdminCommands;
import code.handler.message.InlineKeyboardButtonBuilder;
import code.handler.message.InlineKeyboardButtonListBuilder;
import code.handler.message.MessageHandle;
import code.handler.steps.*;
import code.handler.store.ChatButtonsStore;
import code.handler.store.GptTokenStore;
import code.util.*;
import code.util.gpt.GPTRole;
import code.util.gpt.GPTTranscriptionsModel;
import code.util.gpt.parameter.GPTChatParameter;
import code.util.gpt.parameter.GPTCreateImageParameter;
import code.util.gpt.parameter.GPTMessage;
import code.util.gpt.parameter.GPTTranscriptionsParameter;
import code.util.gpt.response.GPTChatResponse;
import code.util.gpt.response.GPTCreateImageResponse;
import code.util.gpt.response.GPTTranscriptionsResponse;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static code.Main.*;

@Slf4j
public class Handler {

    private final static int CharacterLength = 150;
    private final static String VarRecordContent = "${content}";

    private static GPTChatParameter buildGPTChatParameter(String fromId, List<GPTMessage> messages, String content) {
        GPTMessage message = new GPTMessage();
        message.setRole(GPTRole.User.getRole());
        message.setContent(content);
        messages.add(message);

        GPTChatParameter parameter = new GPTChatParameter();
//        parameter.setUser(fromId);
        parameter.setStream(true);
        parameter.setModel(GlobalConfig.getGptModel());
        parameter.setMessages(messages);

        return parameter;
    }
    private static GPTChatParameter buildGPTChatParameter(String fromId, List<GPTMessage> messages) {
        GPTChatParameter parameter = new GPTChatParameter();
//        parameter.setUser(fromId);
        parameter.setStream(true);
        parameter.setModel(GlobalConfig.getGptModel());
        parameter.setMessages(messages);

        return parameter;
    }

    private static boolean voiceHandle(StepsChatSession session) {
        Voice voice = session.getVoice();
        if (null == voice) {
            return true;
        }
        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Processing), false);
        String ogg = Config.TempDir + "/" + voice.getFileUniqueId() + ".ogg";
        String mp3 = Config.TempDir + "/" + voice.getFileUniqueId() + ".mp3";
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(voice.getFileId());
            File file = Bot.execute(getFile);
            Bot.downloadFile(file, new java.io.File(ogg));
            FFmpegUtil.oggFileToMp3(Config.FFMPEGPath, ogg, mp3);

            String token = GptTokenStore.getRandomToken();

            GPTTranscriptionsParameter parameter = new GPTTranscriptionsParameter();
            parameter.setModel(GPTTranscriptionsModel.Whisper_1.getModel());
            parameter.setFile(new java.io.File(mp3));
            GPTTranscriptionsResponse response = GPTUtil.transcriptions(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), parameter);
            if (!response.isOk()) {
                MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
                return false;
            }

            session.setText(response.getText());

            MessageHandle.deleteMessage(message);
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
            return false;
        } finally {
            new java.io.File(ogg).delete();
            new java.io.File(mp3).delete();
        }
        return true;
    }

    private static String getQuestionText(StepsChatSession session) {
        return (session.getText().length() > CharacterLength ? StringUtils.substring(session.getText(), 0, CharacterLength) + "..." : session.getText());
    }

    private static boolean conciseReplies() {
        Boolean conciseReplies = GlobalConfig.getConciseReplies();
        return BooleanUtils.toBooleanDefaultIfNull(conciseReplies, false);
    }

    public static void init() {
        AdminCommands.init();

        // Chat
        StepsBuilder
                .create()
                .bindCommand(Command.ChatShorter, Command.Chat)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.ThisChatIsANewChat), false);
                    context.put("message", message);
                    return StepResult.next(session.getText());
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        context.put("message2", message);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsContinuousChatMode));

                    List<List<InlineKeyboardButton>> build = null;
                    Optional<ChatButtonsStore.ChatButtonsToInlineKeyboardButtons> buttons = ChatButtonsStore.get();
                    if (buttons.isPresent()) {
                        Optional<List<InlineKeyboardButton>> inlineKeyboardButtonList = buttons.get().getButtons(session.getChatId());
                        if (inlineKeyboardButtonList.isPresent()) {
                            build = InlineKeyboardButtonListBuilder
                                    .create()
                                    .add(inlineKeyboardButtonList.get())
                                    .build();
                        }
                    }
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        Message finalMessage = message;
                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(finalMessage, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        int statusCode = response.getStatusCode();
                        if (statusCode == 400) {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.ChatHasTooManyConversations, response.getStatusCode()));
                            return StepResult.reject();
                        } else {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                            return StepResult.reject();
                        }
                    }

                    StringBuilder builder = new StringBuilder();
                    if (conciseReplies()) {
                        builder.append(response.getContent());
                    } else {
                        builder.append(questionText);
                        builder.append("\n");
                        builder.append("---");
                        builder.append("\n");
                        builder.append(response.getContent());
                        builder.append("\n\n");
                        builder.append("---");
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.ContinueThisChat));
                    }

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString(), build)) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    Object message1 = context.get("message");
                    if (null != message1) {
                        context.remove("message");
                        MessageHandle.deleteMessage((Message) message1);
                    }
                    Object message2 = context.get("message2");
                    if (null != message2) {
                        context.remove("message2");
                        MessageHandle.deleteMessage((Message) message2);
                    }

                    return StepResult.reject();
                })
                .build();

        // Playback
        StepsBuilder
                .create()
                .bindCommand(Command.Playback, Command.PlaybackRegex)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeThePlaybackAlias), false);
                        return StepResult.end();
                    }
                    RecordTableEntity recordTableEntity = RecordTableRepository.selectOneByAlias(text, session.getFromId());
                    if (null == recordTableEntity) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.XXXNotFound, text), false);
                        return StepResult.end();
                    }
                    List<GPTMessage> messageList = JSON.parseArray(recordTableEntity.getChatTemplateJson(), GPTMessage.class);
                    boolean containContentVar = false;
                    for (GPTMessage gptMessage : messageList) {
                        if (gptMessage.getRole().equals(GPTRole.User.getRole())) {
                            String content = gptMessage.getContent();
                            if (content.contains(VarRecordContent)) {
                                containContentVar = true;
                                break;
                            }
                        }
                    }
                    context.put("chatTemplateJson", recordTableEntity.getChatTemplateJson());
                    context.put("messages", Collections.synchronizedList(messageList));
                    context.put("containContentVar", containContentVar);

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.ThisChatIsANewChat), false);
                    context.put("message", message);
                    session.setText(null);
                    return StepResult.next();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        context.put("message2", message);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsContinuousChatMode));
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    Boolean containContentVar = (Boolean) context.get("containContentVar");
                    String chatTemplateJson = (String) context.get("chatTemplateJson");
                    GPTChatParameter gptChatParameter = null;
                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }
                    if (containContentVar) {
                        List<GPTMessage> messageList = JSON.parseArray(chatTemplateJson, GPTMessage.class);
                        for (GPTMessage gptMessage : messageList) {
                            if (gptMessage.getRole().equals(GPTRole.User.getRole())) {
                                String content = gptMessage.getContent();
                                if (content.contains(VarRecordContent)) {
                                    gptMessage.setContent(StringUtils.replace(content, VarRecordContent, session.getText()));
                                }
                            }
                        }
                        gptChatParameter = buildGPTChatParameter(session.getSessionId(), messageList);
                    } else {
                        gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    }

                    AtomicInteger count = new AtomicInteger();
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        int statusCode = response.getStatusCode();
                        if (statusCode == 400) {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.ChatHasTooManyConversations, response.getStatusCode()));
                            return StepResult.reject();
                        } else {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                            return StepResult.reject();
                        }
                    }

                    StringBuilder builder = new StringBuilder();
                    if (conciseReplies()) {
                        builder.append(response.getContent());
                    } else {
                        builder.append(questionText);
                        builder.append("\n");
                        builder.append("---");
                        builder.append("\n");
                        builder.append(response.getContent());
                        builder.append("\n\n");
                        builder.append("---");
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.ContinueThisChat));
                    }

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    Object message1 = context.get("message");
                    if (null != message1) {
                        context.remove("message");
                        MessageHandle.deleteMessage((Message) message1);
                    }
                    Object message2 = context.get("message2");
                    if (null != message2) {
                        context.remove("message2");
                        MessageHandle.deleteMessage((Message) message2);
                    }

                    return StepResult.reject();
                })
                .build();

        // Ask
        StepsBuilder
                .create()
                .bindCommand(Command.Ask, Command.AskShorter)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        context.put("message2", message);
                        return StepResult.reject();
                    }

                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(),  questionText, "...");
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                        return StepResult.end();
                    }

                    StringBuilder builder = new StringBuilder();
                    builder.append(questionText);
                    builder.append("\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(response.getContent());
                    builder.append("\n\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.AskChatEnded));

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    Object message2 = context.get("message2");
                    if (null != message2) {
                        context.remove("message2");
                        MessageHandle.deleteMessage((Message) message2);
                    }

                    return StepResult.end();
                })
                .build();

        // Chat message limit
        int chatMessageLimit = 10;
        StepsBuilder
                .create()
                .bindCommand(Command.ChatMsgLimit)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.ThisChatIsANewChat), false);
                    context.put("message", message);
                    return StepResult.next(session.getText());
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        context.put("message2", message);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsChatMessageLimitMode));
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                        return StepResult.reject();
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    StringBuilder builder = new StringBuilder();
                    builder.append(questionText);
                    builder.append("\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(response.getContent());
                    builder.append("\n\n");
                    builder.append("---");
                    builder.append("\n");
                    if ((messages.size() / 2) >= chatMessageLimit) {
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.CmlChatEnded, (messages.size() / 2), chatMessageLimit));
                    } else {
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.CmlContinueThisChat, (messages.size() / 2), chatMessageLimit));
                    }

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }
                    if ((messages.size() / 2) >= chatMessageLimit) {
                        return StepResult.end();
                    }

                    Object message1 = context.get("message");
                    if (null != message1) {
                        context.remove("message");
                        MessageHandle.deleteMessage((Message) message1);
                    }
                    Object message2 = context.get("message2");
                    if (null != message2) {
                        context.remove("message2");
                        MessageHandle.deleteMessage((Message) message2);
                    }

                    return StepResult.reject();
                })
                .build();

        // None of context chat message
        StepsBuilder
                .create()
                .bindCommand(Command.NoneOfContextChatMessage)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        context.put("message2", message);
                        return StepResult.reject();
                    }

                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsNoneOfMessageContextMode));
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    List<GPTMessage> messages = Collections.synchronizedList(new ArrayList<>());
                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                        return StepResult.reject();
                    }

                    StringBuilder builder = new StringBuilder();
                    builder.append(questionText);
                    builder.append("\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(response.getContent());
                    builder.append("\n\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.ContinueThisChat));

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    Object message2 = context.get("message2");
                    if (null != message2) {
                        context.remove("message2");
                        MessageHandle.deleteMessage((Message) message2);
                    }

                    return StepResult.reject();
                })
                .build();

        // Image
        StepsBuilder
                .create()
                .bindCommand(Command.Image)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAnImageDescription), false);
                        return StepResult.reject();
                    }
                    if (session.getText().length() > 1000) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.ImageDescriptionTextCharacterCountMoreThan), false);
                        return StepResult.reject();
                    }

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Getting), false);

                    GPTCreateImageParameter parameter = new GPTCreateImageParameter();
                    parameter.setUser(session.getSessionId());
                    parameter.setPrompt(session.getText());

                    String token = GptTokenStore.getRandomToken();

                    GPTCreateImageResponse image = GPTUtil.createImage(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), parameter);
                    if (image.isOk()) {
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.Downloading));
                        InputStream inputStream = DownloadUtil.download(RequestProxyConfig.create(), image.getData().get(0).getUrl());
                        MessageHandle.sendImage(session.getChatId(), session.getReplyToMessageId(), "", inputStream);
                        MessageHandle.deleteMessage(message);
                    } else {
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, "-1"));
                    }

                    return StepResult.end();
                })
                .build();

        // Record
        StepsBuilder
                .create()
                .bindCommand(Command.Record)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.RecordModeOpened), false);
                    return StepResult.next(session.getText());
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText()) && null == session.getVoice()) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    boolean result = voiceHandle(session);
                    if (!result) {
                        return StepResult.reject();
                    }

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }
                    if ("end_record".equals(session.getText()) && messages.size() > 0) {
                        return StepResult.next();
                    } else if (session.getText().contains(VarRecordContent)) {
                        GPTMessage gptMessage = new GPTMessage();
                        gptMessage.setRole(GPTRole.User.getRole());
                        gptMessage.setContent(session.getText());
                        messages.add(gptMessage);
                        context.put("messages", messages);
                        return StepResult.next();
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsContinuousChatMode));
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    String token = null;
                    for (int i = 0; i < 3; i++) {
                        token = GptTokenStore.getRandomToken();

                        response = GPTUtil.chat(RequestProxyConfig.create(), token, GlobalConfig.getOpenaiAPIPrefix(), gptChatParameter, (content -> {
                            if (GlobalConfig.getDebug()) {
                                log.info(JSON.toJSONString(content));
                            }
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        GptTokenStore.handle(token, response);
                        try {
                            TimeUnit.SECONDS.sleep(i + 1);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        log.warn(JSON.toJSONString(response));

                        int statusCode = response.getStatusCode();
                        if (statusCode == 400) {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.ChatHasTooManyConversations, response.getStatusCode()));
                            return StepResult.reject();
                        } else {
                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.AnErrorOccurredOfRequestingOpenAiApiFailed, response.getStatusCode()));
                            return StepResult.reject();
                        }
                    }

                    StringBuilder builder = new StringBuilder();
                    builder.append(questionText);
                    builder.append("\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(response.getContent());
                    builder.append("\n\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.ContinueThisChat));

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    return StepResult.reject();
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeRecordAlias), false);
                    return StepResult.ok();
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text) || text.length() > 10) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.InvalidAlias), false);
                        return StepResult.reject();
                    }
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeRecordExplanations), false);
                    context.put("alias", text);
                    return StepResult.ok();
                } , (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text) || text.length() > 100) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.InvalidExplanations), false);
                        return StepResult.reject();
                    }
                    String alias = (String) context.get("alias");
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Saving), false);

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = (List<GPTMessage>) messagesObj;

                    RecordTableEntity recordTableEntity = new RecordTableEntity();
                    recordTableEntity.setId(StringUtils.remove(UUID.randomUUID().toString(), "-"));
                    recordTableEntity.setRecordAlias(alias);
                    recordTableEntity.setRecordExplains(text);
                    recordTableEntity.setChatId(session.getFromId());
                    recordTableEntity.setCreateTime(System.currentTimeMillis());
                    recordTableEntity.setChatTemplateJson(JSON.toJSONString(messages));

                    Boolean insert = RecordTableRepository.insert(recordTableEntity);
                    if (null != insert && insert) {
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.SaveSucceeded));
                    } else {
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.SaveFailed));
                    }
                    return StepResult.ok();
                })
                .build();

        // Record list
        StepsBuilder
                .create()
                .bindCommand(Command.RecordList)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    List<RecordTableEntity> recordTableEntityList = RecordTableRepository.selectListByChatId(session.getFromId());
                    if (recordTableEntityList.size() > 0) {
                        StringBuilder builder = new StringBuilder();
                        InlineKeyboardButtonBuilder buttonBuilder = InlineKeyboardButtonBuilder.create();
                        for (RecordTableEntity recordTableEntity : recordTableEntityList) {
                            builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.RecordAlias) + ": ");
                            builder.append(recordTableEntity.getRecordAlias());
                            builder.append("\n");
                            builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.RecordExplanations) + ": ");
                            builder.append(recordTableEntity.getRecordExplains());
                            builder.append("\n");

                            buttonBuilder.add(recordTableEntity.getRecordAlias(), StepsCenter.buildCallbackData(true, session, Command.GetRecord, recordTableEntity.getId()));
                        }
                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), buttonBuilder.build());
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouCanUseRecordToStartRecoding), false);
                    }

                    return StepResult.ok();
                })
                .build();

        // Get record
        StepsBuilder
                .create()
                .bindCommand(Command.GetRecord)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    RecordTableEntity recordTableEntity = RecordTableRepository.selectOne(session.getText(), session.getFromId());
                    if (null != recordTableEntity) {
                        StringBuilder builder = new StringBuilder();
                        InlineKeyboardButtonBuilder buttonBuilder = InlineKeyboardButtonBuilder.create();

                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.RecordAlias) + ": ");
                        builder.append(recordTableEntity.getRecordAlias());
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.RecordExplanations) + ": ");
                        builder.append(recordTableEntity.getRecordExplains());
                        builder.append("\n");

                        buttonBuilder.add("删除", StepsCenter.buildCallbackData(true, session, Command.DeleteRecord, recordTableEntity.getId()));

                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), buttonBuilder.build());
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouCanUseRecordToStartRecoding), false);
                    }

                    return StepResult.ok();
                })
                .build();

        // Delete record
        StepsBuilder
                .create()
                .bindCommand(Command.DeleteRecord)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    RecordTableEntity recordTableEntity = RecordTableRepository.selectOne(session.getText(), session.getFromId());
                    if (null != recordTableEntity) {
                        Boolean delete = RecordTableRepository.delete(session.getText(), session.getFromId());
                        if (null != delete && delete) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.DeleteSucceeded), false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.DeleteFailed), false);
                        }
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouCanUseRecordToStartRecoding), false);
                    }

                    return StepResult.ok();
                })
                .build();

        // Exit
        StepsBuilder
                .create()
                .bindCommand(Command.Exit)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    StepsCenter.exit(session);

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.ExitSucceeded), false);
                    return StepResult.end();
                })
                .build();


    }

}
