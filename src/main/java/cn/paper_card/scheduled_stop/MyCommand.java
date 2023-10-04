package cn.paper_card.scheduled_stop;

import cn.paper_card.mc_command.TheMcCommand;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

class MyCommand extends TheMcCommand.HasSub {

    private final @NotNull ScheduledStop plugin;
    private final @NotNull Permission permission;

    private final @NotNull TextComponent prefix;

    MyCommand(@NotNull ScheduledStop plugin) {
        super("scheduled-stop");
        this.plugin = plugin;

        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission(this.getLabel() + ".command"));

        this.addSubCommand(new Start());
        this.addSubCommand(new Cancel());

        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.GOLD))
                .append(Component.text("ScheduledStop").color(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("]").color(NamedTextColor.GOLD))
                .build();

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    private void sendError(@NotNull CommandSender commandSender, @NotNull String error) {
        commandSender.sendMessage(
                Component.text()
                        .append(this.prefix)
                        .appendSpace()
                        .append(Component.text(error).color(NamedTextColor.DARK_RED))
                        .build()
        );
    }

    private void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(info).color(NamedTextColor.GREEN))
                .build()
        );
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private class Start extends TheMcCommand {

        private final @NotNull Permission permission;

        // start <消息> <秒数>

        protected Start() {
            super("start");
            this.permission = plugin.addPermission(MyCommand.this.permission.getName() + "." + getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argMessage = strings.length > 0 ? strings[0] : null;
            final String argSeconds = strings.length > 1 ? strings[1] : null;

            if (argMessage == null) {
                sendError(commandSender, "你必须提供参数：消息");
                return true;
            }

            if (argSeconds == null) {
                sendError(commandSender, "你必须提供参数：秒数");
                return true;
            }

            final long secs;

            try {
                secs = Long.parseLong(argSeconds);
            } catch (NumberFormatException e) {
                sendError(commandSender, "%s 不是一个正确的秒数！".formatted(argSeconds));
                return true;
            }

            if (secs <= 0) {
                sendError(commandSender, "秒数应该是大于0的正整数！");
                return true;
            }

            final MyScheduledTask task = plugin.getTask();


            if (task != null) {
                sendInfo(commandSender, "已经有一个计划在执行！");
                return true;
            }

            plugin.startTask(argMessage, secs);
            sendInfo(commandSender, "已经启动计划关闭任务");

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final LinkedList<String> list = new LinkedList<>();
            if (strings.length == 1) {
                final String arg = strings[0];
                if (arg.isEmpty()) list.add("<消息>");
            }

            if (strings.length == 2) {
                final String arg = strings[1];
                if (arg.isEmpty()) list.add("<秒数>");
            }

            return list;
        }
    }

    private class Cancel extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Cancel() {
            super("cancel");
            this.permission = plugin.addPermission(MyCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            final MyScheduledTask task = plugin.getTask();


            if (task == null) {
                sendInfo(commandSender, "当前没有任何关服计划");
                return true;
            }

            plugin.stopTask();
            sendInfo(commandSender, "已取消关服计划");
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }
}
