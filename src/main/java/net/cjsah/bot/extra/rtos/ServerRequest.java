package net.cjsah.bot.extra.rtos;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.alibaba.fastjson2.JSONObject;
import net.cjsah.bot.exception.BuiltExceptions;
import net.cjsah.bot.util.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class ServerRequest {
    private static String Address;
    private static String Token;

    public static void update(JSONObject config) {
        Address = config.getString("address");
        Token = config.getString("token");
    }

    public static void command(String command) {
        request("/command", Method.POST, request -> {
            JSONObject body = JSONObject.of("command", command);
            request.body(JsonUtil.serialize(body), "application/json;charset=UTF-8");
        });
    }

    public static void send(String user, String msg) {
        request("/send", Method.POST, request -> {
            JSONObject body = JSONObject.of("player", user, "msg", msg);
            request.body(JsonUtil.serialize(body), "application/json;charset=UTF-8");
        });
    }

    public static List<PlayerMsg> receive() {
        JSONObject res = request("/msg", Method.GET, request -> {});
        return res.getList("msgs", PlayerMsg.class);
    }

    private static JSONObject request(String url, Method method, Consumer<HttpRequest> factory) {
        HttpRequest request = HttpRequest.of(Address + url).method(method).header("Authorization", Token);
        factory.accept(request);
        try (HttpResponse response = request.execute()) {
            String bodyStr = new String(response.bodyBytes(), StandardCharsets.UTF_8);
            JSONObject json = JsonUtil.deserialize(bodyStr);
            if (json.getIntValue("code") != 0) {
                throw BuiltExceptions.REQUEST_FAILED.create(json.getString("msg"));
            }
            return json.getJSONObject("data");
        }
    }

}
