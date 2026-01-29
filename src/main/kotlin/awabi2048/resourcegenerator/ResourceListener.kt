package awabi2048.resourcegenerator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 資源ワールド内のイベントを監視するリスナー
 */
class ResourceListener : Listener {
    private val random = Random()
    private val sneakTicks = ConcurrentHashMap<UUID, Int>()
    private val bossBars = ConcurrentHashMap<UUID, BossBar>()

    init {
        // 定期的にスニーク状態と足場内判定を確認するタスク
        object : BukkitRunnable() {
            override fun run() {
                for (player in Bukkit.getOnlinePlayers()) {
                    val uuid = player.uniqueId
                    val world = player.world
                    
                    // 資源ワールド判定
                    if (!isResourceWorld(world)) {
                        cleanupBossBar(uuid)
                        sneakTicks.remove(uuid)
                        continue
                    }

                    // 足場領域内判定
                    if (isInScaffold(player)) {
                        // パーティクル表示
                        spawnParticles(player.world)

                        if (player.isSneaking) {
                            val ticks = sneakTicks.getOrDefault(uuid, 0) + 2 // 2 ticks per execution (10Hz)
                            sneakTicks[uuid] = ticks

                            // ボスバー表示更新
                            updateBossBar(player, ticks)

                            // 3秒 (60 ticks) 経過判定
                            if (ticks >= 60) {
                                teleportPlayerHome(player)
                                sneakTicks.remove(uuid)
                                cleanupBossBar(uuid)
                            }
                        } else {
                            sneakTicks.remove(uuid)
                            cleanupBossBar(uuid)
                        }
                    } else {
                        sneakTicks.remove(uuid)
                        cleanupBossBar(uuid)
                    }
                }
            }
        }.runTaskTimer(ResourceGenerator.instance, 0L, 2L) // 0.1秒間隔
    }

    private fun isResourceWorld(world: World): Boolean {
        for (type in listOf("normal", "nether", "end")) {
            val config = ConfigManager.getResourceConfig(type) ?: continue
            if (world.name.startsWith(config.baseName)) return true
        }
        return false
    }

    private fun isInScaffold(player: Player): Boolean {
        val loc = player.location
        val spawn = player.world.spawnLocation
        val radius = ConfigManager.getScaffoldRadius()
        return Math.abs(loc.blockX - spawn.blockX) <= radius && Math.abs(loc.blockZ - spawn.blockZ) <= radius
    }

    private fun spawnParticles(world: World) {
        val spawn = world.spawnLocation
        val radius = ConfigManager.getScaffoldRadius().toDouble()
        
        // 足場の範囲内にランダムにパーティクルを表示
        for (i in 0 until 5) {
            val offsetX = (random.nextDouble() * 2 - 1) * (radius + 0.5)
            val offsetZ = (random.nextDouble() * 2 - 1) * (radius + 0.5)
            val loc = spawn.clone().add(offsetX, 0.1, offsetZ)
            world.spawnParticle(Particle.CLOUD, loc, 1, 0.0, 0.0, 0.0, 0.01)
        }
    }

    private fun updateBossBar(player: Player, ticks: Int) {
        val uuid = player.uniqueId
        val progress = (ticks.toDouble() / 60.0).coerceIn(0.0, 1.0)
        val bossBar = bossBars.computeIfAbsent(uuid) {
            val bar = Bukkit.createBossBar("§bテレポート準備中...", BarColor.BLUE, BarStyle.SOLID)
            bar.addPlayer(player)
            bar
        }
        bossBar.progress = progress
        bossBar.setTitle("§bテレポート準備中... (${String.format("%.1f", (60 - ticks) / 20.0)}秒)")
    }

    private fun teleportPlayerHome(player: Player) {
        val dest: Location = ConfigManager.getEvacuationLocation()
        player.teleport(dest)
        player.sendMessage("§a[ResourceGenerator] 資源ワールドから帰還しました。")
    }

    private fun cleanupBossBar(uuid: UUID) {
        bossBars.remove(uuid)?.apply {
            removeAll()
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cleanupBossBar(uuid)
        sneakTicks.remove(uuid)
    }

    @EventHandler
    fun onEntityPortal(event: EntityPortalEvent) {
        val entity = event.entity
        val fromWorld = entity.world

        if (entity is Player) return

        val endConfig = ConfigManager.getResourceConfig("end") ?: return
        if (!fromWorld.name.startsWith(endConfig.baseName)) return
        if (fromWorld.environment != World.Environment.THE_END) return

        event.isCancelled = true

        var targetLoc = fromWorld.spawnLocation.clone()
        for (i in 1..50) {
            val rx = random.nextInt(65) - 32
            val rz = random.nextInt(65) - 32
            val topBlock = fromWorld.getHighestBlockAt(rx, rz)
            if (topBlock.type == Material.END_STONE) {
                targetLoc = topBlock.location.add(0.5, 1.1, 0.5)
                break
            }
        }
        
        entity.teleport(targetLoc)
    }
}
