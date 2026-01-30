package awabi2048.resourcegenerator

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
    
    private var evacuationWorldName: String = "world"
    private var evacuationX: Double = 0.5
    private var evacuationY: Double = 64.0
    private var evacuationZ: Double = 0.5
    
    private var broadcastSuccessMessage: String = "§a[ResourceGenerator] 資源ワールド %world_name% の生成が完了しました。"
    private var consoleSuccessMessage: String = "資源ワールド %world_name% の生成に成功しました。ボーダーサイズ: %border_size%"
    private var pregenPriorityMsg: String = "§a[ResourceGenerator] 資源ワールド %world_name% の優先エリア生成が完了しました。テレポートが可能です。"
    private var consolePregenPriorityMsg: String = "資源ワールド %world_name% の優先エリア生成が完了しました。テレポートを許可しました。"
    private var pregenAllCompleteMsg: String = "§a[ResourceGenerator] 資源ワールド %world_name% の全エリア生成が完了しました。"

    private var pregenPriorityDiameter: Int = 1000
    private var pregenDelayTicks: Long = 5
    private var pregenBatchSize: Int = 10

    private var scaffoldMaterial: Material = Material.GLASS
    private var scaffoldRadius: Int = 3

    private var particleType: Particle = Particle.CLOUD
    private var particleCount: Int = 5
    private var particleSpeed: Double = 0.01
    private var particleInterval: Long = 2

    private val resourceConfigs = mutableMapOf<String, ResourceConfig>()

    /**
     * 設定をロードする
     */
    fun load(fileConfig: FileConfiguration) {
        config = fileConfig

        val evacSection = fileConfig.getConfigurationSection("evacuation_location")
        evacuationWorldName = evacSection?.getString("world") ?: "world"
        evacuationX = (evacSection?.getInt("x") ?: 0).toDouble() + 0.5
        evacuationY = (evacSection?.getInt("y") ?: 64).toDouble()
        evacuationZ = (evacSection?.getInt("z") ?: 0).toDouble() + 0.5

        val messageSection = fileConfig.getConfigurationSection("messages")
        broadcastSuccessMessage = messageSection?.getString("broadcast_success") ?: "§a[ResourceGenerator] 資源ワールド %world_name% の生成が完了しました。"
        consoleSuccessMessage = messageSection?.getString("console_success") ?: "資源ワールド %world_name% の生成に成功しました。ボーダーサイズ: %border_size%"
        pregenPriorityMsg = messageSection?.getString("pregen_priority_complete") ?: "§a[ResourceGenerator] 資源ワールド %world_name% の優先エリア生成が完了しました。テレポートが可能です。"
        consolePregenPriorityMsg = messageSection?.getString("console_priority_complete") ?: "資源ワールド %world_name% の優先エリア生成が完了しました。テレポートを許可しました。"
        pregenAllCompleteMsg = messageSection?.getString("pregen_all_complete") ?: "§a[ResourceGenerator] 資源ワールド %world_name% の全エリア生成が完了しました。"

        val pregenSection = fileConfig.getConfigurationSection("pregen")
        pregenPriorityDiameter = pregenSection?.getInt("priority_diameter") ?: 1000
        pregenDelayTicks = (pregenSection?.getInt("delay_ticks") ?: 5).toLong()
        pregenBatchSize = pregenSection?.getInt("batch_size") ?: 10

        val scaffoldSection = fileConfig.getConfigurationSection("scaffold")
        scaffoldMaterial = Material.matchMaterial(scaffoldSection?.getString("material") ?: "GLASS") ?: Material.GLASS
        scaffoldRadius = scaffoldSection?.getInt("radius") ?: 3

        val particleSection = scaffoldSection?.getConfigurationSection("particle")
        particleType = Particle.valueOf(particleSection?.getString("type")?.uppercase() ?: "CLOUD")
        particleCount = particleSection?.getInt("count") ?: 5
        particleSpeed = particleSection?.getDouble("speed") ?: 0.01
        particleInterval = (particleSection?.getInt("interval") ?: 2).toLong()

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

    fun getEvacuationLocation(): Location {
        val world = Bukkit.getWorld(evacuationWorldName) ?: Bukkit.getWorlds()[0]
        return Location(world, evacuationX, evacuationY, evacuationZ)
    }

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

    fun getParticleType(): Particle = particleType
    fun getParticleCount(): Int = particleCount
    fun getParticleSpeed(): Double = particleSpeed
    fun getParticleInterval(): Long = particleInterval
}
