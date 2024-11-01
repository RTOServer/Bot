package net.cjsah.bot.extra.rtos;

import net.cjsah.bot.api.Api;
import net.cjsah.bot.api.MsgBuilder;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.List;

import static net.cjsah.bot.extra.rtos.RTOSPlugin.ChannelId;
import static net.cjsah.bot.extra.rtos.RTOSPlugin.RoomId;

public class ReceiveMsgJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        List<PlayerMsg> msgs = ServerRequest.receive();
        if (msgs.isEmpty()) return;
        for (PlayerMsg msg : msgs) {
            String message = "&lt;%s&gt; %s".formatted(msg.player(), msg.msg());
            Api.sendMsg(new MsgBuilder(RoomId, ChannelId, message));
        }
    }
}
