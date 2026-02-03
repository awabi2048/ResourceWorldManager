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
    private var resourceListener: ResourceListener? = null

    override fun onEnable() {
        // プラグインが有効化された時の処理
        instance = this

        // 設定ファイルの保存（デフォルト設定がない場合は作成）
        saveDefaultConfig()

        // 設定のロード
        ConfigManager.load(config)

        // 事前生成の状態を読み込み
        PregenerationStateManager.load()

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
        resourceListener = ResourceListener()
        server.pluginManager.registerEvents(resourceListener!!, this)

        // スコアボードマネージャーを初期化
        ScoreboardManager.init()

        // 中断されていた事前生成を再開
        WorldManager.resumePregeneration()
    }

    override fun onDisable() {
        // すべての事前生成タスクをキャンセル
        WorldManager.cancelAllPregenTasks()

        // リスナーのタスクをキャンセル
        resourceListener?.cancelMonitorTask()

        // スコアボードマネージャーを終了
        ScoreboardManager.disable()

        pluginLogger.info("ResourceWorldManagerが無効になりました。")
    }
}
