package code.handler;

import code.config.*;
import code.eneity.RecordTableEntity;
import code.handler.steps.*;
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
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
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

    private static boolean isAdmin(String fromId) {
        return GlobalConfig.getBotAdminId().equals(fromId);
    }

    private static int getVersionInt(String version) {
        String v = version.replaceAll("\\.", "");
        v = v.replaceAll("v", "");
        v = v.replaceAll("version", "");
        return Integer.valueOf(v).intValue();
    }

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

            GPTTranscriptionsParameter parameter = new GPTTranscriptionsParameter();
            parameter.setModel(GPTTranscriptionsModel.Whisper_1.getModel());
            parameter.setFile(new java.io.File(mp3));
            GPTTranscriptionsResponse response = GPTUtil.transcriptions(RequestProxyConfig.create(), parameter);
            if (!response.isOk()) {
                MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
                return false;
            }

            session.setText(response.getText());
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
            return false;
        } finally {
            new java.io.File(ogg).delete();
            new java.io.File(mp3).delete();
            MessageHandle.deleteMessage(message);
        }
        return true;
    }

    private static String getQuestionText(StepsChatSession session) {
        return (session.getText().length() > CharacterLength ? StringUtils.substring(session.getText(), 0, CharacterLength) + "..." : session.getText());
    }

    public static void init() {
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
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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
                .bindCommand(Command.Playback)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "请发送给我要重放的别名", false);
                        return StepResult.end();
                    }
                    RecordTableEntity recordTableEntity = RecordTableRepository.selectOneByAlias(text, session.getFromId());
                    if (null == recordTableEntity) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), String.format("没有别名为: %s 的录制对话", text), false);
                        return StepResult.end();
                    }
                    context.put("messages", Collections.synchronizedList(JSON.parseArray(recordTableEntity.getChatTemplateJson(), GPTMessage.class)));

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
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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

                    GPTCreateImageResponse image = GPTUtil.createImage(RequestProxyConfig.create(), parameter);
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
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "已开启录制对话， 请先跟机器人模拟对话， 结束录制请发送 end_record 给我", false);
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
                    }

                    String questionText = getQuestionText(session);

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, GlobalConfig.getGptModel(), questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsContinuousChatMode));
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), sendText, false);

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(session.getSessionId(), messages, session.getText());
                    GPTChatResponse response = null;
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
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
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "请发送给我这段录制对话的别名， 在调用时需要用到", false);
                    return StepResult.ok();
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text) || text.length() > 10) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "别名不合法， 长度需要保持在10个字符以内， 请重新发送给我", false);
                        return StepResult.reject();
                    }
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "请发送给我这段录制对话的解释， 显示列表时需要用到", false);
                    context.put("alias", text);
                    return StepResult.ok();
                } , (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text) || text.length() > 100) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "别名解释不合法， 别名解释长度需要保持在100个字符以内， 请重新发送给我", false);
                        return StepResult.reject();
                    }
                    String alias = (String) context.get("alias");
                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "正在保存...", false);

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
                        MessageHandle.editMessage(message, "保存成功");
                    } else {
                        MessageHandle.editMessage(message, "保存失败");
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
                            builder.append("录制别名: ");
                            builder.append(recordTableEntity.getRecordAlias());
                            builder.append("\n");
                            builder.append("录制解释: ");
                            builder.append(recordTableEntity.getRecordExplains());
                            builder.append("\n");

                            buttonBuilder.add(recordTableEntity.getRecordAlias(), StepsCenter.buildCallbackData(true, session, Command.GetRecord, recordTableEntity.getId()));
                        }
                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), buttonBuilder.build());
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "你还没有录制对话， 可以使用 /record 命令进行录制", false);
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

                        builder.append("录制别名: ");
                        builder.append(recordTableEntity.getRecordAlias());
                        builder.append("\n");
                        builder.append("录制解释: ");
                        builder.append(recordTableEntity.getRecordExplains());
                        builder.append("\n");

                        buttonBuilder.add("删除", StepsCenter.buildCallbackData(true, session, Command.DeleteRecord, recordTableEntity.getId()));

                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), buttonBuilder.build());
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "你还没有录制对话， 可以使用 /record 命令进行录制", false);
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
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "删除成功", false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "删除失败", false);
                        }
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "你还没有录制对话， 可以使用 /record 命令进行录制", false);
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

        // Admin
        StepsBuilder
                .create()
                .bindCommand(Command.Admin)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.ChangeModel), StepsCenter.buildCallbackData(true, session, Command.ChangeModel, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetOpenStatus), StepsCenter.buildCallbackData(true, session, Command.SetOpenStatus, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.UpdateConfig), StepsCenter.buildCallbackData(true, session, Command.UpdateConfig, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Restart), StepsCenter.buildCallbackData(true, session, Command.Restart, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Upgrade), StepsCenter.buildCallbackData(true, session, Command.Upgrade, null))
                            .build();
                    ConfigSettings config = Config.readConfig();

                    StringBuilder builder = new StringBuilder();
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.SetOpenStatus) + ": ");
                    builder.append(config.getOpen() ? I18nHandle.getText(session.getFromId(), I18nEnum.Open) : I18nHandle.getText(session.getFromId(), I18nEnum.Close));
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.Model) + ": ");
                    builder.append(config.getGptModel());

                    MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(),  buttons);

                    return StepResult.end();
                })
                .build();

        // Set open status
        StepsBuilder
                .create()
                .bindCommand(Command.SetOpenStatus)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Open), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "open"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Close), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "close"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "cancel"))
                            .build();

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.ChooseOpenStatus),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("open") || text.equals("close")) {
                        ConfigSettings config = Config.readConfig();
                        config.setOpen(text.equals("open"));
                        boolean b = Config.saveConfig(config);
                        if (b) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                        }

                        return StepResult.end();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                })
                .build();

        // Change model
        StepsBuilder
                .create()
                .bindCommand(Command.ChangeModel)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeTheModelYouWantToChange), false);
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "gpt-4, gpt-4-0314, gpt-4-32k, gpt-4-32k-0314, gpt-3.5-turbo, gpt-3.5-turbo-0301", false);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();

                    ConfigSettings config = Config.readConfig();
                    config.setGptModel(text);
                    boolean b = Config.saveConfig(config);
                    if (b) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                    }
                    return StepResult.ok();
                })
                .build();

        // Update config
        StepsBuilder
                .create()
                .bindCommand(Command.UpdateConfig)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm), StepsCenter.buildCallbackData(false, session, Command.UpdateConfig, "confirm"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.UpdateConfig, "cancel"))
                            .build();
                    ConfigSettings config = Config.readConfig();

                    MessageHandle.sendMessage(session.getFromId(), JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat), false);
                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToUpdateTheConfig),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("confirm")) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeConfigContent), false);
                        return StepResult.ok();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    ConfigSettings configSettings = Config.verifyConfig(text);
                    if (null == configSettings) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateConfigFail), false);
                        return StepResult.reject();
                    }

                    boolean b = Config.saveConfig(configSettings);
                    if (b) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                    }

                    return StepResult.end();
                })
                .build();

        // Language
        StepsBuilder
                .create()
                .bindCommand(Command.Language)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    ArrayList<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
                    for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(value.getDisplayText());
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Language, value.getAlias()));

                        inlineKeyboardButtons.add(inlineKeyboardButton);
                    }

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.LanguageList), inlineKeyboardButtons);

                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    I18nLocaleEnum alias = I18nLocaleEnum.getI18nLocaleEnumByAlias(session.getText());

                    I18nHandle.save(session.getFromId(), alias);

                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.ChangeLanguageFinish), false);

                    return StepResult.end();
                })
                .build();

        // Restart
        StepsBuilder
                .create()
                .bindCommand(Command.Restart)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                    inlineKeyboardButton.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm));
                    inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Restart, "true"));

                    InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                    inlineKeyboardButton2.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel));
                    inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Restart, "false"));

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToRestartRightNow), inlineKeyboardButton, inlineKeyboardButton2);

                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Restarting), false);
                        ProgramUtil.restart(Config.MetaData.ProcessName);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                    }
                    return StepResult.end();
                })
                .build();

        // Upgrade
        StepsBuilder
                .create()
                .bindCommand(Command.Upgrade)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession stepsChatSession) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(stepsChatSession.getChatId(), I18nHandle.getText(stepsChatSession.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.GettingUpdateData), false);
                    GithubUtil.LatestReleaseResponse release = GithubUtil.getLatestRelease(RequestProxyConfig.create(), Config.MetaData.GitOwner, Config.MetaData.GitRepo);
                    if (release.isOk()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToUpgradeThisBotRightNow));
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.TargetVersion) + ": ");
                        builder.append(release.getTagName());
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.CurrentVersion) + ": ");
                        builder.append(Config.MetaData.CurrentVersion);
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.UpdateLogs) + ": ");
                        builder.append("\n");
                        builder.append(release.getBody());

                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm));
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Upgrade, "true"));

                        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                        inlineKeyboardButton2.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel));
                        inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Upgrade, "false"));

                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), inlineKeyboardButton, inlineKeyboardButton2);

                        String url = "";
                        for (GithubUtil.LatestReleaseAsset asset : release.getAssets()) {
                            if (Config.MetaData.JarName.equals(asset.getName())) {
                                url = asset.getBrowserDownloadUrl();
                                break;
                            }
                        }

                        context.put("url", url);

                        return StepResult.ok();
                    } else {
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
                        return StepResult.end();
                    }
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Updating), false);
                        String url = (String) context.get("url");

                        AtomicInteger count = new AtomicInteger();
                        String temp = System.getProperty("user.dir") + "/temp.jar";
                        log.info("temp: " + temp);
                        boolean b = DownloadUtil.download(
                                RequestProxyConfig.create(),
                                url,
                                temp,
                                (String var1, String var2, Long var3, Long var4) -> {
                                    if ((var4 - var3) > 0) {
                                        count.incrementAndGet();
                                        if (count.get() == 100) {
                                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.Downloaded, BytesUtil.toDisplayStr(var3), BytesUtil.toDisplayStr(var4)));
                                            count.set(0);
                                        }
                                    }
                                }
                        );

                        if (b) {
                            System.exit(1);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                        }

                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                    }
                    return StepResult.end();
                })
                .build();
    }

}
