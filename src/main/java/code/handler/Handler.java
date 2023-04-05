package code.handler;

import code.config.*;
import code.handler.steps.*;
import code.util.*;
import code.util.gpt.GPTRole;
import code.util.gpt.parameter.GPTChatParameter;
import code.util.gpt.parameter.GPTCreateImageParameter;
import code.util.gpt.parameter.GPTMessage;
import code.util.gpt.response.GPTChatResponse;
import code.util.gpt.response.GPTCreateImageResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static code.Main.GlobalConfig;

@Slf4j
public class Handler {

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
        parameter.setModel("gpt-3.5-turbo");
        parameter.setMessages(messages);

        return parameter;
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
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsContinuousChatMode));
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

                    Object o = context.get("message");
                    if (null != o) {
                        context.remove("message");
                        MessageHandle.deleteMessage((Message) o);
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
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, questionText, "...");
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
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        return StepResult.reject();
                    }
                    return StepResult.next(session.getText());
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(),I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeAProblemThatYouWantToAsk), false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsChatMessageLimitMode));
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

                    Object o = context.get("message");
                    if (null != o) {
                        context.remove("message");
                        MessageHandle.deleteMessage((Message) o);
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
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(),"请发送给我想要问的问题...", false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = I18nHandle.getText(session.getFromId(), I18nEnum.RequestingOpenAiApi, questionText, I18nHandle.getText(session.getFromId(), I18nEnum.TheCurrentModeIsNoneOfMessageContextMode));
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
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.UpdateConfig), StepsCenter.buildCallbackData(true, session, Command.UpdateConfig, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Restart), StepsCenter.buildCallbackData(true, session, Command.Restart, null))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Upgrade), StepsCenter.buildCallbackData(true, session, Command.Upgrade, null))
                            .build();

                    MessageHandle.sendInlineKeyboard(session.getChatId(), "Admin",  buttons);
                    return StepResult.end();
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
