package net.cjsah.bot.extra.rtos;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSONObject;
import net.cjsah.bot.FilePaths;
import net.cjsah.bot.command.Command;
import net.cjsah.bot.command.CommandManager;
import net.cjsah.bot.command.CommandParam;
import net.cjsah.bot.command.source.CommandSource;
import net.cjsah.bot.event.EventManager;
import net.cjsah.bot.event.events.UserJoinEvent;
import net.cjsah.bot.plugin.Plugin;
import net.cjsah.bot.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class RTOSPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger("RTOSPlugin");
    private static final FilePaths.AppFile ConfigPath = FilePaths.regFile(FilePaths.CONFIG.resolve("rtos.json"), "{\"host\":\"\",\"port\":25575,\"password\":\"\",\"users\":[]}");
    private static final String RoomId = "";

    @Override
    public void onLoad() {
        ConfigPath.checkAndCreate();
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

    @Command("/bind")
    public static void bindUser(@CommandParam("id") String id, CommandSource source) throws IOException, InterruptedException {
        if (!RoomId.equals(source.sender().getRoomInfo().getId())) return;
        String str = ConfigPath.read();
        JSONObject config = JsonUtil.deserialize(str, JSONObject.class);
        List<RTOSUser> users = config.getList("users", RTOSUser.class);
        long senderId = source.sender().getSenderInfo().getId();
        if (users.stream().anyMatch(it -> Objects.equals(it.heyId(), senderId))) {
            source.sendFeedback("你已绑定正版账号, 无法再次绑定, 如需解绑换绑请联系管理员!");
            return;
        }
        RTOSUser user = new RTOSUser(senderId, id);
        RTOSPlugin.sendCommand(
                config.getString("host"),
                config.getIntValue("port"),
                config.getString("password"),
                "whitelist add " + id
        );
        config.getJSONArray("users").add(user);
        FileUtil.writeString(JsonUtil.serialize(config), ConfigPath.path().toFile(), StandardCharsets.UTF_8);
    }

    private static void userLeave(long heyId) throws IOException, InterruptedException {
        String str = ConfigPath.read();
        JSONObject config = JsonUtil.deserialize(str, JSONObject.class);
        List<RTOSUser> users = config.getList("users", RTOSUser.class);
        if (users.stream().anyMatch(it -> Objects.equals(it.heyId(), heyId))) {
            RTOSPlugin.sendCommand(
                    config.getString("host"),
                    config.getIntValue("port"),
                    config.getString("password"),
                    "whitelist remove " + heyId
            );
            users.removeIf(it -> Objects.equals(it.heyId(), heyId));
            config.put("users", users);
            FileUtil.writeString(JsonUtil.serialize(config), ConfigPath.path().toFile(), StandardCharsets.UTF_8);
        }
    }

    private static void sendCommand(String host, int port, String password, String command) throws IOException, InterruptedException {
        RconClient client = new RconClient();
        client.connect(host, port, password);
        client.command(command);
        client.shutdown();
    }
}
