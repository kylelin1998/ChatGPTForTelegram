package code.config;

import lombok.Getter;

@Getter
public enum I18nEnum {

    BotStartSucceed("bot_start_succeed"),
    HelpText("help_text"),

    InvalidCommand("invalid_command"),
    On("on"),
    Off("off"),
    Test("test"),
    Update("update"),
    NotFound("not_found"),
    UnknownError("unknown_error"),
    NothingAtAll("nothing_at_all"),
    CancelSucceeded("cancel_succeeded"),
    Confirm("confirm"),
    Cancel("cancel"),
    Delete("delete"),
    Finish("finish"),
    ExitSucceeded("exit_succeeded"),
    Getting("getting"),
    Downloading("downloading"),
    UpdateConfig("update_config"),
    Restart("restart"),
    Upgrade("upgrade"),
    UpdateSucceeded("update_succeeded"),
    UpdateFailed("update_failed"),
    Open("open"),
    Close("close"),

    LanguageList("language_list"),
    ChangeLanguageFinish("change_language_finish"),

    ThisChatIsANewChat("this_chat_is_a_new_chat"),
    AreYouSureToUpdateTheConfig("are_you_sure_to_update_the_config"),
    PleaseSendMeConfigContent("please_send_me_config_content"),
    UpdateConfigFail("update_config_fail"),
    PleaseSendMeAProblemThatYouWantToAsk("please_send_me_a_problem_that_you_want_to_ask"),
    RequestingOpenAiApi("requesting_open_ai_api"),
    TheCurrentModeIsContinuousChatMode("the_current_mode_is_continuous_chat_mode"),
    AnErrorOccurredOfRequestingOpenAiApiFailed("an_error_occurred_of_requesting_open_ai_api_failed"),
    ContinueThisChat("continue_this_chat"),
    AskChatEnded("ask_chat_ended"),
    TheCurrentModeIsChatMessageLimitMode("the_current_mode_is_chat_message_limit_mode"),
    CmlChatEnded("cml_chat_ended"),
    CmlContinueThisChat("cml_continue_this_chat"),
    TheCurrentModeIsNoneOfMessageContextMode("the_current_mode_is_none_of_message_context_mode"),

    YouAreNotAnAdmin("you_are_not_an_admin"),
    AreYouSureToRestartRightNow("are_you_sure_to_restart_right_now"),
    Restarting("restarting"),
    GettingUpdateData("getting_update_data"),
    AreYouSureToUpgradeThisBotRightNow("are_you_sure_to_upgrade_this_bot_right_now"),
    TargetVersion("target_version"),
    CurrentVersion("current_version"),
    UpdateLogs("update_logs"),
    Updating("updating"),
    Downloaded("downloaded"),
    PleaseSendMeAnImageDescription("please_send_me_an_image_description"),
    ImageDescriptionTextCharacterCountMoreThan("image_description_text_character_count_more_than"),

    ChatHasTooManyConversations("chat_has_too_many_conversations"),
    SetOpenStatus("set_open_status"),
    ChooseOpenStatus("choose_open_status"),

    ;

    private String key;

    I18nEnum(String key) {
        this.key = key;
    }

}
