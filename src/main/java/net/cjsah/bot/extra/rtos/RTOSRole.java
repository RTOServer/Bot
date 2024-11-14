package net.cjsah.bot.extra.rtos;

import net.cjsah.bot.data.RoleInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum RTOSRole {
    CODER("编程", ""),
    RED_STONE("红石", ""),
    BUILDER("建筑", ""),
    LOGISTICS("后勤", "")
    ;

    private final String name;
    private final String team;

    RTOSRole(String name, String team) {
        this.name = name;
        this.team = team;
        InnerClass.ROLE_MAP.put(name, this);
    }

    public String getName() {
        return this.name;
    }

    public String getTeam() {
        return this.team;
    }

    public static boolean contains(List<RoleInfo> roles) {
        return roles.stream().map(RoleInfo::getName).anyMatch(InnerClass.ROLE_MAP::containsKey);
    }

    private static class InnerClass {
        private static final Map<String, RTOSRole> ROLE_MAP = new HashMap<>();
    }

}
