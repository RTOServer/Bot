package net.cjsah.bot.extra.rtos;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSONObject;
import net.cjsah.bot.FilePaths;
import net.cjsah.bot.api.Api;
import net.cjsah.bot.command.Command;
import net.cjsah.bot.command.CommandManager;
import net.cjsah.bot.command.CommandParam;
import net.cjsah.bot.command.source.CommandSource;
import net.cjsah.bot.data.RoleInfo;
import net.cjsah.bot.event.EventManager;
import net.cjsah.bot.event.events.CommandEvent;
import net.cjsah.bot.event.events.UserJoinEvent;
import net.cjsah.bot.exception.BuiltExceptions;
import net.cjsah.bot.exception.CommandException;
import net.cjsah.bot.plugin.Plugin;
import net.cjsah.bot.util.JsonUtil;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RTOSPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger("RTOSPlugin");
    private static final FilePaths.AppFile ConfigPath = FilePaths.regFile(FilePaths.CONFIG.resolve("rtos.json"), "{\"address\":\"\",\"token\":\"\",\"users\":[]}");
    private static final List<RTOSUser> Users = new ArrayList<>();
    public static final String RoomId = "3691550761648357376";
    public static final String ChannelId = "3703962297047187456";

    private Scheduler scheduler;

    @Override
    public void onLoad() {
        ConfigPath.checkAndCreate();
        RTOSPlugin.readConfig();
        CommandManager.register(RTOSPlugin.class);
        EventManager.subscribe(UserJoinEvent.class, it -> {
            if (!RoomId.equals(it.getRoomInfo().getId())) return;
            try {
                RTOSPlugin.userLeave(it.getUserInfo().getId());
            } catch (IOException | InterruptedException e) {
                log.error("Fail to leave user", e);
            }
        });
        try {
            SchedulerFactory factory = new StdSchedulerFactory();
            this.scheduler = factory.getScheduler();
            this.scheduler.start();
        } catch (SchedulerException e) {
            log.error("Fail to create scheduler", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onStarted() {
        JobDetail job = JobBuilder
                .newJob(ReceiveMsgJob.class)
                .withIdentity("Job", "Msg")
                .build();
        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity("Trigger", "Msg")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(500).repeatForever())
                .build();
        try {
            this.scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.error("Fail to create schedule job", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUnload() {
        TriggerKey triggerKey = new TriggerKey("Trigger", "Msg");
        JobKey jobKey = new JobKey("Job", "Msg");
        try {
            this.scheduler.pauseTrigger(triggerKey);
            this.scheduler.unscheduleJob(triggerKey);
            this.scheduler.deleteJob(jobKey);
            this.scheduler.shutdown(true);
        } catch (SchedulerException e) {
            log.error("Error stopping schedule", e);
        }
    }

    private static JSONObject readConfig() {
        String str = ConfigPath.read();
        JSONObject config = JsonUtil.deserialize(str, JSONObject.class);
        ServerRequest.update(config);
        Users.clear();
        Users.addAll(config.getList("users", RTOSUser.class));
        return config;
    }

    private static void saveConfig(JSONObject config) {
        config.put("users", Users);
        FileUtil.writeString(JsonUtil.serialize(config), ConfigPath.path().toFile(), StandardCharsets.UTF_8);
    }

    @Command("/bind")
    public static void bindUser(@CommandParam("id") String id, @CommandParam("role") String role, CommandSource source) {
        if (!RoomId.equals(source.sender().getRoomInfo().getId()))
            throw BuiltExceptions.DISPATCHER_COMMAND_NO_PERMISSION.create();
        JSONObject config = RTOSPlugin.readConfig();
        long senderId = source.sender().getSenderInfo().getId();
        if (Users.stream().anyMatch(it -> Objects.equals(it.heyId(), senderId))) {
            throw new CommandException("你已绑定正版账号, 无法再次绑定, 如需解换绑请联系管理员!");
        }
        RoleInfo roleSet = Api.getRoomRoles(RoomId)
                .stream()
                .filter(it -> role.equals(it.getName()))
                .findFirst().orElse(null);
        if (roleSet == null) {
            throw new CommandException("身份组不存在");
        }
        RTOSUser user = new RTOSUser(senderId, id);
        ServerRequest.command("whitelist add " + id);
        Api.userGiveRole((int) senderId, RoomId, roleSet.getId());
        Users.add(user);
        RTOSPlugin.saveConfig(config);
        source.sendFeedback("绑定成功!");
    }

    @Command("/mc <msg>")
    public static void sendMsg(String msg, CommandSource source) {
        CommandEvent sender = source.sender();
        long senderId = sender.getSenderInfo().getId();
        RTOSUser user = Users.stream()
                .filter(it -> Objects.equals(it.heyId(), senderId))
                .findFirst()
                .orElseThrow(() -> new CommandException("未绑定MC正版账号, 清先使用/bind绑定"));
        ServerRequest.send(user.mcId(), msg);
        Api.msgReplyEmoji(sender.getRoomInfo().getId(), sender.getChannelInfo().getId(), sender.getMsgId(), "[1_\uD83D\uDC4C]", true);
    }

    private static void userLeave(long heyId) throws IOException, InterruptedException {
        JSONObject config = RTOSPlugin.readConfig();
        RTOSUser user = Users.stream().filter(it -> Objects.equals(it.heyId(), heyId)).findFirst().orElse(null);
        if (user != null) {
            ServerRequest.command("whitelist remove " + user.mcId());
            Users.removeIf(it -> Objects.equals(it.heyId(), heyId));
            RTOSPlugin.saveConfig(config);
        }
    }
}
