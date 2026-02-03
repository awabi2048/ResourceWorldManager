package awabi2048.resource_world_manager

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard

object ScoreboardManager {
    private val monitoringPlayers = mutableSetOf<java.util.UUID>()
    private var updateTask: org.bukkit.scheduler.BukkitTask? = null

    fun init() {
        updateTask = object : BukkitRunnable() {
            override fun run() {
                updateScoreboards()
            }
        }.runTaskTimer(ResourceWorldManager.instance, 0L, 40L) // 2秒ごとに更新
    }

    fun toggleMonitor(player: Player): String {
        if (monitoringPlayers.contains(player.uniqueId)) {
            monitoringPlayers.remove(player.uniqueId)
            player.scoreboard = Bukkit.getScoreboardManager()?.newScoreboard
            return "§c[ResourceWorldManager] モニターをオフにしました。"
        } else {
            monitoringPlayers.add(player.uniqueId)
            return "§a[ResourceWorldManager] モニターをオンにしました。"
        }
    }

    private fun updateScoreboards() {
        for (uuid in monitoringPlayers) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            val scoreboard = createScoreboard(player)
            player.scoreboard = scoreboard
        }
    }

    private fun createScoreboard(player: Player): Scoreboard {
        val scoreboardManager = Bukkit.getScoreboardManager() ?: return player.scoreboard
        val scoreboard = scoreboardManager.newScoreboard
        val objective = scoreboard.registerNewObjective("resource_monitor", "dummy", "§f§lResource World Monitor")
        objective.displaySlot = DisplaySlot.SIDEBAR

        val separator = "§7§m――――――――――――――――――"
        val header = "§f§l| §7ワールド | 仮読み込み | 全読み込み"

        var line = 15
        objective.getScore(separator).score = line--
        objective.getScore(header).score = line--
        objective.getScore(separator).score = line--

        // 全ての資源ワールドを取得
        val allResourceConfigs = ConfigManager.getAllResourceConfigs()
        val worlds = Bukkit.getWorlds()

        for ((type, config) in allResourceConfigs) {
            for (variation in config.variations) {
                val prefix = "${config.baseName}.${variation.lowercase()}."
                val world = worlds.find { it.name.startsWith(prefix) } ?: continue

                val worldName = "§e${type}:${variation}"

                // 事前読み込み状況
                val pregenProgress = WorldManager.getPregenProgress(world.name)
                val priorityCompleteTime = WorldManager.getPriorityCompleteTime(world.name)
                val allCompleteTime = WorldManager.getAllCompleteTime(world.name)

                val pregenStatus: String
                if (allCompleteTime != null) {
                    pregenStatus = "§d完了"
                } else if (priorityCompleteTime != null) {
                    // 優先エリア完了済み、全エリア進行中
                    pregenStatus = "§d完了"
                } else if (pregenProgress > 0) {
                    val estimatedMinutes = calculateEstimatedMinutes(world.name, priorityCompleteTime == null)
                    pregenStatus = "§b${pregenProgress}%§7(約${estimatedMinutes}分)"
                } else {
                    pregenStatus = "§7未開始"
                }

                // 全チャンク読み込み状況
                val allStatus: String
                if (allCompleteTime != null) {
                    allStatus = "§d読み込み終了"
                } else if (priorityCompleteTime != null) {
                    val estimatedMinutes = calculateEstimatedMinutes(world.name, false)
                    allStatus = "§b${pregenProgress}%§7(約${estimatedMinutes}分)"
                } else {
                    val estimatedMinutes = calculateEstimatedMinutes(world.name, true)
                    allStatus = "§b${pregenProgress}%§7(約${estimatedMinutes}分)"
                }

                val lineText = "§f§l| $worldName $pregenStatus §f| $allStatus"
                objective.getScore(lineText).score = line--
            }
        }

        objective.getScore(separator).score = line

        return scoreboard
    }

    private fun calculateEstimatedMinutes(worldName: String, isPriority: Boolean): Int {
        val taskInfo = WorldManager.getPregenTasks()[worldName]
        if (taskInfo == null) return 0

        val pregenProgress = WorldManager.getPregenProgress(worldName)
        if (pregenProgress == 0) return 0

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - taskInfo.startTime
        val elapsedTimeMinutes = elapsedTime / 60000.0

        // 予想総時間 = 経過時間 / 進捗率 * 100
        val estimatedTotalMinutes = elapsedTimeMinutes / pregenProgress * 100
        val remainingMinutes = (estimatedTotalMinutes - elapsedTimeMinutes).toInt()

        return maxOf(0, remainingMinutes)
    }

    fun disable() {
        updateTask?.cancel()
        updateTask = null

        for (uuid in monitoringPlayers) {
            val player = Bukkit.getPlayer(uuid)
            player?.scoreboard = Bukkit.getScoreboardManager()?.newScoreboard
        }
        monitoringPlayers.clear()
    }
}
