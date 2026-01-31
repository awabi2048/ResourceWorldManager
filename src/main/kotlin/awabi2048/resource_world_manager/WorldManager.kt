package awabi2048.resource_world_manager

import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * 資源ワールドの管理を行うマネージャー
 */
object WorldManager {
    private val logger: Logger = ResourceWorldManager.instance.logger
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    
    // テレポート準備ができたワールド名のセット
    private val readyWorlds = mutableSetOf<String>()
    
    // 現在の生成進捗 (ワールド名 -> 進捗率%)
    private val pregenProgress = mutableMapOf<String, Int>()

    /**
     * 資源ワールドを生成する
     */
    fun generateResourceWorld(type: String, variation: String, customBorderSize: Int? = null): Boolean {
        val resourceConfig = ConfigManager.getResourceConfig(type) ?: run {
            logger.warning("リソースタイプ $type の設定が見つかりません。")
            return false
        }

        if (!resourceConfig.variations.contains(variation.lowercase())) {
            logger.warning("バリエーション $variation はリソースタイプ $type に定義されていません。")
            return false
        }

        // 1. 既存の資源ワールドを削除
        deleteResourceWorld(type, variation)

        // 2. 新しいワールド名を決定
        val dateStr = LocalDateTime.now().format(dateFormatter)
        val worldName = "${resourceConfig.baseName}.$variation.$dateStr"

        // 3. ワールド生成の設定
        val creator = WorldCreator(worldName)
        when (type.lowercase()) {
            "nether" -> creator.environment(World.Environment.NETHER)
            "end" -> creator.environment(World.Environment.THE_END)
            else -> creator.environment(World.Environment.NORMAL)
        }

        logger.info("資源ワールド $worldName を生成しています...")
        val world = creator.createWorld() ?: run {
            val errorMsg = "ワールド $worldName の生成に失敗しました。"
            logger.severe(errorMsg)
            Bukkit.broadcastMessage("§c[ResourceWorldManager] $errorMsg")
            return false
        }

        // 4. ワールドボーダーの設定
        val borderSize = customBorderSize ?: resourceConfig.defaultBorder
        val border = world.worldBorder
        border.setCenter(0.5, 0.5)
        border.size = borderSize.toDouble()

        // 5. スポーン地点の設定
        val spawnLoc: Location = calculateSpawnLocation(world)
        world.setSpawnLocation(spawnLoc)

        // 6. メッセージの出力
        val broadcastMsg = ConfigManager.getBroadcastSuccessMessage()
            .replace("%world_name%", worldName)
            .replace("%border_size%", borderSize.toString())
        val consoleMsg = ConfigManager.getConsoleSuccessMessage()
            .replace("%world_name%", worldName)
            .replace("%border_size%", borderSize.toString())

        Bukkit.broadcastMessage(broadcastMsg)
        logger.info(consoleMsg)
        
        // 7. 足場の生成
        createScaffold(world, spawnLoc)

        // 8. 事前生成プロセスの開始
        startPregeneration(world, borderSize)
        
        return true
    }

    /**
     * スポーン地点に足場を生成する
     */
    private fun createScaffold(world: World, location: Location) {
        val material = ConfigManager.getScaffoldMaterial()
        val radius = ConfigManager.getScaffoldRadius()
        val centerX = location.blockX
        val centerY = location.blockY - 1
        val centerZ = location.blockZ

        for (x in -radius..radius) {
            for (z in -radius..radius) {
                world.getBlockAt(centerX + x, centerY, centerZ + z).type = material
            }
        }
        logger.info("ワールド ${world.name} のスポーン地点に半径 $radius の足場を生成しました (${material.name})")
    }

    private fun calculateSpawnLocation(world: World): Location {
        return when (world.environment) {
            World.Environment.NETHER -> {
                var foundY = 64.0
                for (y in 120 downTo 1) {
                    val block = world.getBlockAt(0, y, 0)
                    if (block.type.isSolid) {
                        foundY = (y + 1).toDouble()
                        break
                    }
                }
                Location(world, 0.5, foundY, 0.5)
            }
            World.Environment.THE_END -> {
                var bestLoc = Location(world, 0.5, (world.getHighestBlockAt(0, 0).y + 1).toDouble(), 0.5)
                val random = java.util.Random()
                for (i in 1..100) {
                    val rx = random.nextInt(65) - 32
                    val rz = random.nextInt(65) - 32
                    val topBlock = world.getHighestBlockAt(rx, rz)
                    if (topBlock.type == Material.END_STONE) {
                        bestLoc = topBlock.location.add(0.5, 1.0, 0.5)
                        break
                    }
                }
                bestLoc
            }
            else -> {
                Location(world, 0.5, (world.getHighestBlockAt(0, 0).y + 1).toDouble(), 0.5)
            }
        }
    }

    /**
     * チャンクの事前生成を開始する
     */
    private fun startPregeneration(world: World, borderSize: Int) {
        val priorityDiameter = ConfigManager.getPregenPriorityDiameter()
        val delay = ConfigManager.getPregenDelayTicks()
        val batchSize = ConfigManager.getPregenBatchSize()
        
        // 生成すべき全チャンクの座標リストを作成
        val chunks = mutableListOf<ChunkCoords>()
        val radiusChunks = (borderSize / 2 / 16) + 1
        
        for (x in -radiusChunks..radiusChunks) {
            for (z in -radiusChunks..radiusChunks) {
                chunks.add(ChunkCoords(x, z))
            }
        }
        
        // 優先ゾーン（スポーン周辺）をリストの先頭に持ってくる
        val priorityRadius = (priorityDiameter / 2 / 16) + 1
        val priorityChunks = chunks.filter { Math.abs(it.x) <= priorityRadius && Math.abs(it.z) <= priorityRadius }
        val remainingChunks = chunks.filter { !priorityChunks.contains(it) }
        
        val sortedChunks = priorityChunks + remainingChunks
        val totalChunks = sortedChunks.size
        
        object : BukkitRunnable() {
            var index = 0
            var lastReportedPercent = -1

            override fun run() {
                val endIdx = Math.min(index + batchSize, totalChunks)
                
                for (i in index until endIdx) {
                    val coords = sortedChunks[i]
                    world.getChunkAtAsync(coords.x, coords.z)
                }
                
                index = endIdx
                
                // 進捗報告
                val percent = (index * 100) / totalChunks
                pregenProgress[world.name] = percent
                
                if (percent / 10 > lastReportedPercent / 10) {
                    logger.info("資源ワールド ${world.name} チャンク生成中... $percent%")
                    lastReportedPercent = percent
                }

                // 優先ゾーン完了判定
                if (index >= priorityChunks.size && !readyWorlds.contains(world.name)) {
                    readyWorlds.add(world.name)
                    val msg = ConfigManager.getPregenPriorityMessage().replace("%world_name%", world.name)
                    Bukkit.broadcastMessage(msg)
                    
                    val consoleMsg = ConfigManager.getConsolePregenPriorityMessage().replace("%world_name%", world.name)
                    logger.info(ChatColor.stripColor(consoleMsg))
                }

                // 全完了判定
                if (index >= totalChunks) {
                    val msg = ConfigManager.getPregenAllCompleteMessage().replace("%world_name%", world.name)
                    logger.info(ChatColor.stripColor(msg))
                    pregenProgress.remove(world.name)
                    this.cancel()
                }
            }
        }.runTaskTimer(ResourceWorldManager.instance, 0L, delay)
    }

    data class ChunkCoords(val x: Int, val z: Int)

    fun isWorldReady(worldName: String): Boolean = readyWorlds.contains(worldName)
    
    fun getPregenProgress(worldName: String): Int = pregenProgress[worldName] ?: 0

    /**
     * 指定されたリソースタイプとバリエーションに該当する既存ワールドを削除する
     */
    fun deleteResourceWorld(type: String, variation: String) {
        val resourceConfig = ConfigManager.getResourceConfig(type) ?: return
        val prefix = "${resourceConfig.baseName}.$variation."

        val worldsToRemove = Bukkit.getWorlds().filter { it.name.startsWith(prefix) }
        
        for (world in worldsToRemove) {
            val worldName = world.name
            readyWorlds.remove(worldName)
            pregenProgress.remove(worldName)
            
            // プレイヤーを避難させる
            val evacuationCmd = ConfigManager.getEvacuationCommand()
            for (player in world.players) {
                player.performCommand(evacuationCmd)
                player.sendMessage("§e[ResourceWorldManager] 資源ワールドが再生成されるため、避難しました。")
            }

            Bukkit.unloadWorld(world, false)
            logger.info("ワールド $worldName をアンロードしました。")
        }

        // ファイルシステム上のデータを削除
        val worldContainer = Bukkit.getWorldContainer()
        val files = worldContainer.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && file.name.startsWith(prefix)) {
                deleteWorldFolder(file)
                logger.info("ワールドフォルダ ${file.name} を削除しました。")
            }
        }
    }

    private fun deleteWorldFolder(path: java.io.File) {
        if (path.exists()) {
            val files = path.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        deleteWorldFolder(file)
                    } else {
                        file.delete()
                    }
                }
            }
            path.delete()
        }
    }

    /**
     * サーバー起動時に既存の資源ワールドをロードする
     */
    fun loadExistingWorlds() {
        logger.info("既存の資源ワールドをスキャンしています...")
        val worldContainer = Bukkit.getWorldContainer()
        val files = worldContainer.listFiles() ?: return
        
        val resourceConfigs = ConfigManager.getAllResourceConfigs()
        val worldsToLoad = mutableMapOf<String, java.io.File>() // "baseName.variation" -> latest folder

        for (file in files) {
            if (!file.isDirectory) continue
            val nameParts = file.name.split(".")
            if (nameParts.size != 3) continue

            val baseName = nameParts[0]
            val variation = nameParts[1]
            val dateStr = nameParts[2]

            // 対応する設定があるか確認
            val configEntry = resourceConfigs.entries.find { it.value.baseName == baseName } ?: continue
            if (!configEntry.value.variations.contains(variation)) continue

            val key = "$baseName.$variation"
            val existing = worldsToLoad[key]
            if (existing == null || file.name > existing.name) {
                // 古い方は削除対象（または単に無視）
                if (existing != null) {
                    logger.info("古い資源ワールド ${existing.name} をスキップします。")
                }
                worldsToLoad[key] = file
            }
        }

        for ((key, folder) in worldsToLoad) {
            val worldName = folder.name
            if (Bukkit.getWorld(worldName) == null) {
                logger.info("既存の資源ワールド $worldName をロードしています...")
                val creator = WorldCreator(worldName)
                
                // 環境設定の復元
                val type = resourceConfigs.entries.find { key.startsWith(it.value.baseName) }?.key ?: "normal"
                when (type.lowercase()) {
                    "nether" -> creator.environment(World.Environment.NETHER)
                    "end" -> creator.environment(World.Environment.THE_END)
                    else -> creator.environment(World.Environment.NORMAL)
                }
                
                if (creator.createWorld() != null) {
                    readyWorlds.add(worldName)
                    logger.info("資源ワールド $worldName のロードに成功しました。")
                } else {
                    logger.severe("資源ワールド $worldName のロードに失敗しました。")
                }
            } else {
                readyWorlds.add(worldName)
            }
        }
    }

    /**
     * プレイヤーを資源ワールドに転送する
     */
    fun teleportToResourceWorld(player: Player, type: String, variation: String): Boolean {
        val resourceConfig = ConfigManager.getResourceConfig(type) ?: return false
        val prefix = "${resourceConfig.baseName}.${variation.lowercase()}."
        
        val world = Bukkit.getWorlds().find { it.name.startsWith(prefix) } ?: run {
            player.sendMessage("§c[ResourceWorldManager] 指定された資源ワールドが存在しません。生成してください。")
            return false
        }

        if (!isWorldReady(world.name)) {
            val progress = getPregenProgress(world.name)
            player.sendMessage("§c[ResourceWorldManager] 資源ワールドは現在準備中です。優先エリアの生成をお待ちください ($progress%)")
            return false
        }

        player.teleport(world.spawnLocation)
        player.sendMessage("§a[ResourceWorldManager] 資源ワールド (${type}:${variation}) に移動しました。")
        return true
    }
}
