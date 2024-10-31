package net.cjsah.bot.extra.rtos;

import com.alibaba.fastjson2.annotation.JSONField;

public record RTOSUser(@JSONField(name = "hey_id") long heyId, @JSONField(name = "mc_id") String mcId) {

    @Override
    public long heyId() {
        return this.heyId;
    }

    @Override
    public String mcId() {
        return this.mcId;
    }
}
