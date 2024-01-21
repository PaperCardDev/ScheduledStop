package cn.paper_card.scheduled_stop;

import cn.paper_card.afk.api.AfkPlayer;
import cn.paper_card.afk.api.PaperCardAfkApi;
import cn.paper_card.qq_bind.api.BindInfo;
import cn.paper_card.qq_bind.api.QqBindApi;
import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import cn.paper_card.smurf.api.SmurfApi;
import cn.paper_card.smurf.api.SmurfInfo;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScheduledStop extends JavaPlugin implements Listener {

    private MyScheduledTask task = null;
    private final @NotNull BossBar bossBar;

    private final @NotNull Object lockTask = new Object();
    private final @NotNull Object lockWait = new Object();

    private boolean cancelTask = false;

    private final @NotNull TaskScheduler scheduler;

    private QqGroupAccessApi qqGroupAccessApi = null;
    private QqBindApi qqBindApi = null;
    private PaperCardAfkApi paperCardAfkApi = null;

    private SmurfApi smurfApi = null;

    public ScheduledStop() {
        this.bossBar = this.getServer().createBossBar(null, BarColor.YELLOW, BarStyle.SEGMENTED_20);
        this.scheduler = UniversalScheduler.getScheduler(this);
    }

    void startTask(@NotNull String msg, long secs) {
        synchronized (this.lockTask) {
            if (this.task == null) {
                this.task = this.scheduler.runTaskAsynchronously(new Run(msg, secs));
            }
        }
    }

    void stopTask() {
        synchronized (lockTask) {
            if (this.task == null) return;
        }

        synchronized (lockWait) {
            cancelTask = true;
            lockWait.notify();

            try {
                lockWait.wait(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (lockTask) {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    @Nullable MyScheduledTask getTask() {
        synchronized (lockTask) {
            return this.task;
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (this.bossBar.isVisible()) this.bossBar.addPlayer(event.getPlayer());
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.bossBar.setVisible(false);

        new MyCommand(this);

        this.qqGroupAccessApi = this.getServer().getServicesManager().load(QqGroupAccessApi.class);
        if (this.qqGroupAccessApi == null) {
            this.getSLF4JLogger().warn("无法连接到" + QqGroupAccessApi.class.getSimpleName());
        } else {
            this.getSLF4JLogger().info("已连接到" + QqGroupAccessApi.class.getSimpleName());
        }

        this.qqBindApi = this.getServer().getServicesManager().load(QqBindApi.class);
        this.paperCardAfkApi = this.getServer().getServicesManager().load(PaperCardAfkApi.class);
        this.smurfApi = this.getServer().getServicesManager().load(SmurfApi.class);
    }

    @Override
    public void onDisable() {
        this.stopTask();
    }

    private class Run implements Runnable {

        private final @NotNull String msg;
        private final long secs;

        private Run(@NotNull String msg, long secs) {
            this.msg = msg;
            this.secs = secs;
        }

        private @NotNull String minuteAndSeconds(long secs) {
            final long minutes = secs / 60;
            secs %= 60;

            final StringBuilder sb = new StringBuilder();

            if (minutes != 0) {
                sb.append(minutes);
                sb.append('分');
            }

            sb.append(secs);
            sb.append('秒');

            return sb.toString();
        }

        private void notifyAfkPlayersInGroup(long secs, @NotNull String msg) {
            final QqGroupAccessApi qqGroupAccessApi1 = qqGroupAccessApi;
            if (qqGroupAccessApi1 == null) {
                getSLF4JLogger().warn("QqGroupAccessApi不可用，无法在QQ群通知AFK玩家");
                return;
            }

            final QqBindApi qqBindApi1 = qqBindApi;
            if (qqBindApi1 == null) {
                getSLF4JLogger().warn("QqBindApi不可用，无法在QQ群通知AFK玩家");
                return;
            }

            final PaperCardAfkApi paperCardAfkApi1 = paperCardAfkApi;
            if (paperCardAfkApi1 == null) {
                getSLF4JLogger().warn("PaperCardAfkApi不可用，无法在QQ群通知AFK玩家");
                return;
            }

            final GroupAccess mainGroupAccess;

            try {
                mainGroupAccess = qqGroupAccessApi1.createMainGroupAccess();
            } catch (Exception e) {
                getSLF4JLogger().error("无法访问QQ主群：", e);
                return;
            }

            // 获取所有AFK玩家的QQ号码
            final HashSet<Long> qqs = new HashSet<>();

            final StringBuilder players = new StringBuilder();
            int count = 0;

            try {
                final SmurfApi smurfApi1 = smurfApi;

                for (Player onlinePlayer : getServer().getOnlinePlayers()) {
                    final AfkPlayer afkPlayer = paperCardAfkApi1.getAfkPlayer(onlinePlayer.getUniqueId());
                    if (afkPlayer == null) continue;

                    final long afkSince = afkPlayer.getAfkSince();

                    if (afkSince <= 0) continue;

                    ++count;
                    players.append(onlinePlayer.getName());
                    players.append('、');

                    boolean added = false;

                    final BindInfo bindInfo = qqBindApi1.getBindService().queryByUuid(onlinePlayer.getUniqueId());

                    if (bindInfo != null) {
                        qqs.add(bindInfo.qq());
                        added = true;
                    }

                    // 如果是小号，通知对应大号的QQ
                    if (smurfApi1 != null) {
                        final SmurfInfo smurfInfo = smurfApi1.getSmurfService().queryBySmurfUuid(onlinePlayer.getUniqueId());
                        if (smurfInfo != null) {
                            final BindInfo qqBind = qqBindApi1.getBindService().queryByUuid(smurfInfo.mainUuid());
                            if (qqBind != null) {
                                qqs.add(qqBind.qq());
                                added = true;
                            }
                        }
                    }

                    if (!added) {
                        getSLF4JLogger().warn("无法通知AFK玩家 %s 下线".formatted(onlinePlayer.getName()));
                    }
                }
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
                return;
            }

            try {
                mainGroupAccess.sendAtMessage(qqs.stream().toList(), """
                        \n
                        服务器即将在%s后：%s
                        请做好下线准备~
                        ----
                        当前%d个AFK玩家：
                        %s""".formatted(
                        minuteAndSeconds(secs), msg,
                        count, players.toString()
                ));
            } catch (Exception e) {
                getSLF4JLogger().error("无法发送群消息", e);
            }
        }

        @Override
        public void run() {
            cancelTask = false;

            final long begin = System.currentTimeMillis();
            final long end = begin + secs * 1000L;

            bossBar.setVisible(false);
            bossBar.setProgress(1.0);

            for (final Player onlinePlayer : getServer().getOnlinePlayers()) {
                bossBar.addPlayer(onlinePlayer);
            }

            bossBar.setVisible(true);


            synchronized (lockWait) {

                this.notifyAfkPlayersInGroup(secs, msg);

                final AtomicBoolean isMusicPlaying = new AtomicBoolean(false);

                for (final Player player : getServer().getOnlinePlayers()) {
                    player.sendTitlePart(TitlePart.TITLE, Component.text("关服倒计时").color(NamedTextColor.RED));
                    player.sendTitlePart(TitlePart.SUBTITLE, Component.text(msg).color(NamedTextColor.GOLD));
                }

                for (long cur = begin; cur < end; cur = System.currentTimeMillis()) {

                    final long delta = end - cur;

                    final double p = (double) delta / (double) (end - begin);
                    bossBar.setProgress(p);

                    bossBar.setTitle("将在%s后: %s".formatted(
                            minuteAndSeconds(delta / 1000L), msg));

                    if (!isMusicPlaying.get() && delta <= 3 * 60 * 1000L + 15 * 1000L) {

                        scheduler.runTask(() -> {
                            getServer().playSound(Sound.sound()
                                    .type(org.bukkit.Sound.MUSIC_DISC_OTHERSIDE.getKey())
                                    .volume(2147483647.0F)
                                    .pitch(1.0F)
                                    .source(Sound.Source.MUSIC)
                                    .build());


                            isMusicPlaying.set(true);
                        });

                    }


                    try {
                        lockWait.wait(1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        getSLF4JLogger().error("", e);
                        bossBar.removeAll();
                        return;
                    }

                    if (cancelTask) {
                        bossBar.removeAll();
                        bossBar.setVisible(false);
                        lockWait.notify();
                        return;
                    }
                }
            }

            scheduler.runTask(() -> getServer().dispatchCommand(getServer().getConsoleSender(),
                    "minecraft:stop"));

            bossBar.removeAll();
            bossBar.setVisible(false);

            synchronized (lockTask) {
                task = null;
            }
        }
    }
}
