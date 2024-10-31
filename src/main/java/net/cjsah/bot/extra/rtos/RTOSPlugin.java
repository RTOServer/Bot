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
import net.cjsah.bot.event.events.UserJoinEvent;
import net.cjsah.bot.exception.BuiltExceptions;
import net.cjsah.bot.exception.CommandException;
import net.cjsah.bot.plugin.Plugin;
import net.cjsah.bot.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RTOSPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger("RTOSPlugin");
    private static final FilePaths.AppFile ConfigPath = FilePaths.regFile(FilePaths.CONFIG.resolve("rtos.json"), "{\"host\":\"\",\"port\":25575,\"password\":\"\",\"users\":[]}");
    private static final String RoomId = "3691550761648357376";
    private static final List<RTOSUser> Users = new ArrayList<>();

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
    }

    private static JSONObject readConfig() {
        String str = ConfigPath.read();
        JSONObject config = JsonUtil.deserialize(str, JSONObject.class);
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
        try {
            RTOSPlugin.sendCommand(
                    config.getString("host"),
                    config.getIntValue("port"),
                    config.getString("password"),
                    "whitelist add " + id
            );
        } catch (IOException | InterruptedException e) {
            log.error("Error", e);
            source.sendFeedback("绑定失败: " + e.getMessage());
            return;
        }
        Api.userGiveRole((int) senderId, RoomId, roleSet.getId());
        Users.add(user);
        RTOSPlugin.saveConfig(config);
        source.sendFeedback("绑定成功!");
    }

    private static void userLeave(long heyId) throws IOException, InterruptedException {
        JSONObject config = RTOSPlugin.readConfig();
        if (Users.stream().anyMatch(it -> Objects.equals(it.heyId(), heyId))) {
            RTOSPlugin.sendCommand(
                    config.getString("host"),
                    config.getIntValue("port"),
                    config.getString("password"),
                    "whitelist remove " + heyId
            );
            Users.removeIf(it -> Objects.equals(it.heyId(), heyId));
            RTOSPlugin.saveConfig(config);
        }
    }

    private static void sendCommand(String host, int port, String password, String command) throws IOException, InterruptedException {
        RconClient client = new RconClient();
        log.info("正在连接到服务器...");
        client.connect(host, port, password);
        log.info("执行命令: {}", command);
        String response = client.command(command);
        log.info("服务器返回: {}", response);
        log.info("断开连接.");
        client.shutdown();
    }
}
