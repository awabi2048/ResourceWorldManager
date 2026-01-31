package awabi2048.resource_world_manager

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * ResourceWorldManagerプラグインのメインクラスです。
 */
class ResourceWorldManager : JavaPlugin() {

    companion object {
        lateinit var instance: ResourceWorldManager
            private set
    }

    private val pluginLogger: Logger = logger

    override fun onEnable() {
        // プラグインが有効化された時の処理
        instance = this

        // 設定ファイルの保存（デフォルト設定がない場合は作成）
        saveDefaultConfig()
        
        // 設定のロード
        ConfigManager.load(config)
        
        // 既存の資源ワールドをロード
        WorldManager.loadExistingWorlds()

        pluginLogger.info("ResourceWorldManagerが有効になりました。")
        
        // コマンドの登録
        val resourceCommand = ResourceCommand()
        getCommand("resource")?.apply {
            setExecutor(resourceCommand)
            tabCompleter = resourceCommand
        }

        // リスナーの登録
        server.pluginManager.registerEvents(ResourceListener(), this)
    }

    override fun onDisable() {
        // プラグインが無効化された時の処理
        pluginLogger.info("ResourceWorldManagerが無効になりました。")
    }
}
