package awabi2048.resource_world_manager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.configuration.file.FileConfiguration

/**
 * 資源の設定情報を保持するデータクラス
 */
data class ResourceConfig(
    val baseName: String,
    val defaultBorder: Int,
    val variations: List<String>
)

/**
 * プラグインの設定を管理するマネージャー
 */
object ConfigManager {
    private var config: FileConfiguration? = null
    
    private var evacuationCommand: String = "spawn"
    
    private var broadcastSuccessMessage: String = "§a資源ワールド %world_name% の生成が完了しました。"
    private var consoleSuccessMessage: String = "資源ワールド %world_name% の生成に成功しました。ボーダーサイズ: %border_size%"
    private var pregenPriorityMsg: String = "§a資源ワールド %world_name% の優先エリア生成が完了しました。テレポートが可能です。"
    private var consolePregenPriorityMsg: String = "資源ワールド %world_name% の優先エリア生成が完了しました。テレポートを許可しました。"
    private var pregenAllCompleteMsg: String = "§a資源ワールド %world_name% の全エリア生成が完了しました。"

    private var pregenPriorityDiameter: Int = 1000
    private var pregenDelayTicks: Long = 5
    private var pregenBatchSize: Int = 10

    private var scaffoldMaterial: Material = Material.GLASS
    private var scaffoldRadius: Int = 3

    private var spawnSearchRadius: Int = 64
    private var spawnSearchAttempts: Int = 200
    private var spawnSafeBlocks: List<Material> = listOf(
        Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
        Material.STONE, Material.COBBLESTONE, Material.SAND, Material.SANDSTONE,
        Material.GRAVEL, Material.MOSS_BLOCK
    )

    private var particleType: Particle = Particle.CLOUD
    private var particleCount: Int = 5
    private var particleSpeed: Double = 0.01
    private var particleInterval: Long = 2

    private var soundStart: String = "BLOCK_NOTE_BLOCK_BELL"
    private var soundSuccess: String = "ENTITY_EXPERIENCE_ORB_PICKUP"
    private var actionBarMessage: String = "§bShift長押しでスポーンに戻ります"

    private val resourceConfigs = mutableMapOf<String, ResourceConfig>()

    // マクロ設定
    private val macroBeforeDelete = mutableListOf<String>()
    private val macroAfterGeneration = mutableListOf<String>()
    private val macroAfterPriorityPregen = mutableListOf<String>()
    private val macroAfterAllPregen = mutableListOf<String>()
    private var macroBeforeDeleteEnabled = false
    private var macroAfterGenerationEnabled = false
    private var macroAfterPriorityPregenEnabled = false
    private var macroAfterAllPregenEnabled = false

    /**
     * 設定をロードする
     */
    fun load(fileConfig: FileConfiguration) {
        config = fileConfig

        evacuationCommand = fileConfig.getString("evacuation_command") ?: "spawn"

        val messageSection = fileConfig.getConfigurationSection("messages")
        broadcastSuccessMessage = messageSection?.getString("broadcast_success") ?: "§a資源ワールド %world_name% の生成が完了しました。"
        consoleSuccessMessage = messageSection?.getString("console_success") ?: "資源ワールド %world_name% の生成に成功しました。ボーダーサイズ: %border_size%"
        pregenPriorityMsg = messageSection?.getString("pregen_priority_complete") ?: "§a資源ワールド %world_name% の優先エリア生成が完了しました。テレポートが可能です。"
        consolePregenPriorityMsg = messageSection?.getString("console_priority_complete") ?: "資源ワールド %world_name% の優先エリア生成が完了しました。テレポートを許可しました。"
        pregenAllCompleteMsg = messageSection?.getString("pregen_all_complete") ?: "§a資源ワールド %world_name% の全エリア生成が完了しました。"

        val pregenSection = fileConfig.getConfigurationSection("pregen")
        pregenPriorityDiameter = pregenSection?.getInt("priority_diameter") ?: 1000
        pregenDelayTicks = (pregenSection?.getInt("delay_ticks") ?: 5).toLong()
        pregenBatchSize = pregenSection?.getInt("batch_size") ?: 10

        val scaffoldSection = fileConfig.getConfigurationSection("scaffold")
        scaffoldMaterial = Material.matchMaterial(scaffoldSection?.getString("material") ?: "GLASS") ?: Material.GLASS
        scaffoldRadius = scaffoldSection?.getInt("radius") ?: 3

        val spawnSection = fileConfig.getConfigurationSection("spawn")
        spawnSearchRadius = spawnSection?.getInt("search_radius") ?: 64
        spawnSearchAttempts = spawnSection?.getInt("search_attempts") ?: 200
        
        val safeBlocksList = spawnSection?.getStringList("safe_blocks")
        if (safeBlocksList != null && safeBlocksList.isNotEmpty()) {
            spawnSafeBlocks = safeBlocksList.mapNotNull { 
                Material.matchMaterial(it.uppercase()) 
            }.filter { it.isBlock }
        }

        val particleSection = scaffoldSection?.getConfigurationSection("particle")
        particleType = Particle.valueOf(particleSection?.getString("type")?.uppercase() ?: "CLOUD")
        particleCount = particleSection?.getInt("count") ?: 5
        particleSpeed = particleSection?.getDouble("speed") ?: 0.01
        particleInterval = (particleSection?.getInt("interval") ?: 2).toLong()

        val soundSection = scaffoldSection?.getConfigurationSection("sound")
        soundStart = soundSection?.getString("start") ?: "BLOCK_NOTE_BLOCK_BELL"
        soundSuccess = soundSection?.getString("success") ?: "ENTITY_EXPERIENCE_ORB_PICKUP"

        actionBarMessage = scaffoldSection?.getString("action_bar_message") ?: "§bShift長押しでスポーンに戻ります"

        // マクロ設定の読み込み
        val macroSection = fileConfig.getConfigurationSection("macros")
        
        val beforeDeleteSection = macroSection?.getConfigurationSection("before_delete")
        macroBeforeDeleteEnabled = beforeDeleteSection?.getBoolean("enabled") ?: false
        macroBeforeDelete.clear()
        if (macroBeforeDeleteEnabled) {
            macroBeforeDelete.addAll(beforeDeleteSection?.getStringList("commands") ?: emptyList())
        }
        
        val afterGenSection = macroSection?.getConfigurationSection("after_generation")
        macroAfterGenerationEnabled = afterGenSection?.getBoolean("enabled") ?: false
        macroAfterGeneration.clear()
        if (macroAfterGenerationEnabled) {
            macroAfterGeneration.addAll(afterGenSection?.getStringList("commands") ?: emptyList())
        }
        
        val afterPrioritySection = macroSection?.getConfigurationSection("after_priority_pregen")
        macroAfterPriorityPregenEnabled = afterPrioritySection?.getBoolean("enabled") ?: false
        macroAfterPriorityPregen.clear()
        if (macroAfterPriorityPregenEnabled) {
            macroAfterPriorityPregen.addAll(afterPrioritySection?.getStringList("commands") ?: emptyList())
        }
        
        val afterAllSection = macroSection?.getConfigurationSection("after_all_pregen")
        macroAfterAllPregenEnabled = afterAllSection?.getBoolean("enabled") ?: false
        macroAfterAllPregen.clear()
        if (macroAfterAllPregenEnabled) {
            macroAfterAllPregen.addAll(afterAllSection?.getStringList("commands") ?: emptyList())
        }

        resourceConfigs.clear()

        val section = fileConfig.getConfigurationSection("resources") ?: return
        for (type in listOf("normal", "nether", "end")) {
            val typeSection = section.getConfigurationSection(type) ?: continue
            val baseName = typeSection.getString("base_name") ?: "resource_$type"
            val defaultBorder = typeSection.getInt("default_border", 5000)
            val variations = typeSection.getStringList("variations")

            resourceConfigs[type] = ResourceConfig(baseName, defaultBorder, variations)
        }
    }

    fun getResourceConfig(type: String): ResourceConfig? = resourceConfigs[type.lowercase()]
    fun getAllResourceConfigs(): Map<String, ResourceConfig> = resourceConfigs

    fun getEvacuationCommand(): String = evacuationCommand

    fun getBroadcastSuccessMessage(): String = broadcastSuccessMessage
    fun getConsoleSuccessMessage(): String = consoleSuccessMessage
    fun getPregenPriorityMessage(): String = pregenPriorityMsg
    fun getConsolePregenPriorityMessage(): String = consolePregenPriorityMsg
    fun getPregenAllCompleteMessage(): String = pregenAllCompleteMsg

    fun getPregenPriorityDiameter(): Int = pregenPriorityDiameter
    fun getPregenDelayTicks(): Long = pregenDelayTicks
    fun getPregenBatchSize(): Int = pregenBatchSize

    fun getScaffoldMaterial(): Material = scaffoldMaterial
    fun getScaffoldRadius(): Int = scaffoldRadius

    fun getSpawnSearchRadius(): Int = spawnSearchRadius
    fun getSpawnSearchAttempts(): Int = spawnSearchAttempts
    fun getSpawnSafeBlocks(): List<Material> = spawnSafeBlocks.toList()

    fun getParticleType(): Particle = particleType
    fun getParticleCount(): Int = particleCount
    fun getParticleSpeed(): Double = particleSpeed
    fun getParticleInterval(): Long = particleInterval

    fun getSoundStart(): String = soundStart
    fun getSoundSuccess(): String = soundSuccess
    fun getActionBarMessage(): String = actionBarMessage

    // マクロ設定のゲッター
    fun isMacroBeforeDeleteEnabled(): Boolean = macroBeforeDeleteEnabled
    fun getMacroBeforeDeleteCommands(): List<String> = macroBeforeDelete.toList()
    
    fun isMacroAfterGenerationEnabled(): Boolean = macroAfterGenerationEnabled
    fun getMacroAfterGenerationCommands(): List<String> = macroAfterGeneration.toList()
    
    fun isMacroAfterPriorityPregenEnabled(): Boolean = macroAfterPriorityPregenEnabled
    fun getMacroAfterPriorityPregenCommands(): List<String> = macroAfterPriorityPregen.toList()
    
    fun isMacroAfterAllPregenEnabled(): Boolean = macroAfterAllPregenEnabled
    fun getMacroAfterAllPregenCommands(): List<String> = macroAfterAllPregen.toList()
}
