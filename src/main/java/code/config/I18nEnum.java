package code.config;

import lombok.Getter;

@Getter
public enum I18nEnum {

    BotStartSucceed("bot_start_succeed"),
    WelcomeText("welcome_text"),
    HelpText("help_text"),

    InvalidCommand("invalid_command"),
    MonitorList("monitor_list"),
    NothingHere("nothing_here"),
    On("on"),
    Off("off"),
    Test("test"),
    Update("update"),
    NotFound("not_found"),
    NotFoundMonitor("not_found_monitor"),
    OnMonitor("on_monitor"),
    OffMonitor("off_monitor"),
    ExitEditMode("exit_edit_mode"),
    UnknownError("unknown_error"),
    NothingAtAll("nothing_at_all"),
    CancelSucceed("cancel_succeed"),
    Confirm("confirm"),
    Cancel("cancel"),
    Delete("delete"),
    Finish("finish"),

    LanguageList("language_list"),
    ChangeLanguageFinish("change_language_finish"),

    DeleteMonitorConfirm("delete_monitor_confirm"),
    DeleteMonitorFinish("delete_monitor_finish"),


    ConfigDisplayStepNumber("config_display_step_number"),


    ChannelName("channel_name"),
    ChannelImage("channel_image"),
    ChannelDescription("channel_description"),
    ChannelSubscribers("channel_subscribers"),
    ChannelIncreaseSubscribers("channel_increase_subscribers"),
    ChannelLink("channel_link"),

    CreateLimitMonitorCountPrompt("create_limit_monitor_count_prompt"),
    CreateStepPleaseSendMeLink("create_step_please_send_me_link"),
    CreateVerifyLink("create_verify_link"),
    CreateVerifyFail("create_verify_fail"),
    CreateStepPleaseSendMeStepNumber("create_step_please_send_me_step_number"),
    CreateVerifyStepNumberFail("create_verify_step_number_fail"),
    CreateFail("create_fail"),
    CreateSuccess("create_success"),
    CreateExistAlready("create_exist_already"),


    UpdateStepChooseOneField("update_step_choose_one_field"),
    UpdateStepPleaseSendMeNewValue("update_step_please_send_me_new_value"),
    UpdateFieldFail("update_field_fail"),
    UpdateSuccess("update_success"),


    QueryWait("query_wait"),

    ;

    private String key;

    I18nEnum(String key) {
        this.key = key;
    }

}
