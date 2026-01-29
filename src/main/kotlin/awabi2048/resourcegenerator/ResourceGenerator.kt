package awabi2048.resourcegenerator

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * ResourceGeneratorプラグインのメインクラスです。
 */
class ResourceGenerator : JavaPlugin() {

    companion object {
        lateinit var instance: ResourceGenerator
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

        pluginLogger.info("ResourceGenerator (Kotlin) が有効になりました。")
        
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
        pluginLogger.info("ResourceGenerator (Kotlin) が無効になりました。")
    }
}
