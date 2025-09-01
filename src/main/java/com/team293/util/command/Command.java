package com.team293.util.command;

import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.team293.Main;
import com.team293.util.Registerable;
import com.team293.util.reflection.ReflectionUtils;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface Command extends Registerable {

    Logger log = LoggerFactory.getLogger(Command.class);

    String getCommand();

    void execute(SlashCommandRequest req, Response res);

    default void register() {
        Main.app.command("/" + getCommand(), (req, ctx) -> {
            Response res = Response.builder().build();
            execute(req, res);
            return res;
        });
    }

    @SneakyThrows
    default void assertWorkspaceAdmin(SlashCommandRequest req) {
        var userId = req.getPayload().getUserId();
        var teamId = req.getPayload().getTeamId();
        var userInfo = Main.app.client().usersInfo(r -> r.user(userId));
        if (!userInfo.isOk() || userInfo.getUser() == null || !userInfo.getUser().isAdmin() ||
                !userInfo.getUser().getTeamId().equals(teamId)) {
            throw new IllegalStateException("This command can only be used by workspace admins of this team.");
        }
    }

    @Override
    default void registerAll() {
        List<Class<? extends Command>> commandClasses = ReflectionUtils.getAllClassesImplementingInterface(
                Command.class,
                "com.team293.commands"
        );

        for (Class<? extends Command> commandClass : commandClasses) {
            try {
                Command command = commandClass.getDeclaredConstructor().newInstance();
                log.info("Registering command: {}", command.getCommand());
                command.register();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    default <T> T getOptionValue(SlashCommandRequest req, int position, Class<T> clazz) {
        String arg = getArgAtPosition(req, position);
        if (arg == null) {
            return null;
        }
        if (clazz == String.class) {
            return clazz.cast(arg);
        } else if (clazz == Integer.class) {
            return clazz.cast(Integer.parseInt(arg));
        } else if (clazz == Long.class) {
            return clazz.cast(Long.parseLong(arg));
        } else if (clazz == Boolean.class) {
            return clazz.cast(Boolean.parseBoolean(arg));
        } else if (clazz == Double.class) {
            return clazz.cast(Double.parseDouble(arg));
        } else if (clazz == Float.class) {
            return clazz.cast(Float.parseFloat(arg));
        }
        throw new IllegalArgumentException("Unsupported option type: " + clazz.getName());
    }

    private String getArgAtPosition(SlashCommandRequest req, int position) {
        var options = req.getPayload().getText().split(" ");
        if (position >= 0 && position < options.length) {
            return options[position];
        }
        return null;
    }

    static Command blank() {
        return new Command() {
            @Override
            public String getCommand() {
                return "";
            }

            @Override
            public void execute(SlashCommandRequest req, Response res) { }
        };
    }

}