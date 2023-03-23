package code.handler;

import code.config.Config;
import code.config.I18nEnum;
import code.config.I18nLocaleEnum;
import code.config.RequestProxyConfig;
import code.handler.steps.*;
import code.util.*;
import code.util.gpt.GPTRole;
import code.util.gpt.parameter.GPTChatParameter;
import code.util.gpt.parameter.GPTCreateImageParameter;
import code.util.gpt.parameter.GPTMessage;
import code.util.gpt.response.GPTChatResponse;
import code.util.gpt.response.GPTCreateImageResponse;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
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
        parameter.setUser(fromId);
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
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(),"请发送给我想要问的问题...", false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = String.format("%s\n---\n此模式为连续对话模式\n正在组织语言...", questionText);
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
                        MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 错误码: %s, 出现了未知错误， 请重新试试吧！！！", response.getStatusCode()));
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
                    builder.append("你可以继续发送给我想要问的问题， 或者使用 /exit 命令来进行退出此次会话");

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
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(),"请发送给我想要问的问题...", false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = String.format("%s\n---\n正在组织语言...", questionText);
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
                        MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 错误码: %s, 出现了未知错误， 请重新试试吧！！！", response.getStatusCode()));
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
                    builder.append("对话结束， 你可以使用 /ask 命令来重新发问");

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
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(),"请发送给我想要问的问题...", false);
                        return StepResult.reject();
                    }

                    String questionText = (session.getText().length() > 15 ? StringUtils.substring(session.getText(), 0, 15) : session.getText()) + "...";

                    String sendText = String.format("%s\n---\n此模式为消息限制次数对话模式\n正在组织语言...", questionText);
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
                        MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 错误码: %s, 出现了未知错误， 请重新试试吧！！！", response.getStatusCode()));
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
                        builder.append(String.format("对话结束， 当前对话次数: %s / %s， 你可以使用 /cml 命令重新发起对话", (messages.size() / 2), chatMessageLimit));
                    } else {
                        builder.append(String.format("你可以继续发送给我想要问的问题， 当前对话次数: %s / %s，或者使用 /exit 命令来进行退出此次会话", (messages.size() / 2), chatMessageLimit));
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
                    return StepResult.reject();
                })
                .build();

        // None context chat message
        StepsBuilder
                .create()
                .bindCommand(Command.NoneContextChatMessage)
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

                    String sendText = String.format("%s\n---\n此模式为无上下文聊天模式\n正在组织语言...", questionText);
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
                        MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 错误码: %s, 出现了未知错误， 请重新试试吧！！！", response.getStatusCode()));
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
                    builder.append("你可以继续发送给我想要问的问题， 或者使用 /exit 命令来进行退出此次会话");

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
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getChatId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(session.getText())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "创建图片失败， 请发送给我想要生成图片的文本解释, 或者输入 /exit 退出", false);
                        return StepResult.reject();
                    }
                    if (session.getText().length() > 1000) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "创建图片失败， 图片要求文本长度不能大于1000字符, 请重新发送给我想要生成图片的文本解释, 或者输入 /exit 退出", false);
                        return StepResult.reject();
                    }

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "正在尝试生成图片...请稍等...", false);

                    try {
                        GPTCreateImageParameter parameter = new GPTCreateImageParameter();
                        parameter.setUser(session.getSessionId());
                        parameter.setPrompt(session.getText());

                        GPTCreateImageResponse image = GPTUtil.createImage(RequestProxyConfig.create(), parameter);
                        if (image.isOk()) {
                            MessageHandle.editMessage(message, "已获取到图片， 正在下载发送...");
                            InputStream inputStream = DownloadUtil.download(RequestProxyConfig.create(), image.getData().get(0).getUrl());
                            MessageHandle.sendImage(session.getChatId(), session.getReplyToMessageId(), "", inputStream);
                        } else {
                            MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 出现了未知错误， 请重新试试吧！！！"));
                        }
                    } finally {
                        MessageHandle.deleteMessage(message);
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
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getChatId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    StepsCenter.exit(session);

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "退出成功", false);
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
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getChatId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    ArrayList<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
                    for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(value.getDisplayText());
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(session, Command.Language, value.getAlias()));

                        inlineKeyboardButtons.add(inlineKeyboardButton);
                    }

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getChatId(), I18nEnum.LanguageList), inlineKeyboardButtons);

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
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getChatId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "你不是管理员， 无法使用此命令", false);
                        return StepResult.end();
                    }

                    InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                    inlineKeyboardButton.setText("确定");
                    inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(session, Command.Restart, "true"));

                    InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                    inlineKeyboardButton2.setText("取消");
                    inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(session, Command.Restart, "false"));

                    MessageHandle.sendInlineKeyboard(session.getChatId(), "确定重启机器人吗？", inlineKeyboardButton, inlineKeyboardButton2);

                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "正在重启...", false);
                        ProgramUtil.restart(Config.MetaData.ProcessName);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "取消成功", false);
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
                    MessageHandle.sendMessage(stepsChatSession.getChatId(), I18nHandle.getText(stepsChatSession.getChatId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "你不是管理员， 无法使用此命令", false);
                        return StepResult.end();
                    }

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "正在获取更新数据...", false);
                    GithubUtil.LatestReleaseResponse release = GithubUtil.getLatestRelease(RequestProxyConfig.create(), Config.MetaData.GitOwner, Config.MetaData.GitRepo);
                    if (release.isOk()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("确定更新机器人吗? ");
                        builder.append("\n");
                        builder.append("目标版本: ");
                        builder.append(release.getTagName());
                        builder.append("\n");
                        builder.append("目前版本: ");
                        builder.append(Config.MetaData.CurrentVersion);
                        builder.append("\n");
                        builder.append("更新内容: ");
                        builder.append("\n");
                        builder.append(release.getBody());

                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText("确定");
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(session, Command.Upgrade, "true"));

                        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                        inlineKeyboardButton2.setText("取消");
                        inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(session, Command.Upgrade, "false"));

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
                        MessageHandle.editMessage(message, "获取更新数据失败...");
                        return StepResult.end();
                    }
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "正在更新...", false);
                        String url = (String) context.get("url");

                        AtomicInteger count = new AtomicInteger();
                        boolean b = DownloadUtil.download(
                                RequestProxyConfig.create(),
                                url,
                                "/" + Config.MetaData.ProcessName,
                                (String var1, String var2, Long var3, Long var4) -> {
                                    count.incrementAndGet();
                                    if (count.get() == 10) {
                                        MessageHandle.editMessage(message, String.format("已下载: %s, 总文件大小: %s", BytesUtil.toDisplayStr(var4 - var3), BytesUtil.toDisplayStr(var4)));
                                        count.set(0);
                                    }
                                }
                        );
                        if (b) {
                            ProgramUtil.restart(Config.MetaData.ProcessName);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "更新失败， 重新试试吧", false);
                        }
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "取消成功", false);
                    }
                    return StepResult.end();
                })
                .build();
    }

}
