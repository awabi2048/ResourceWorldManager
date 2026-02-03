package awabi2048.resource_world_manager

import org.bukkit.Bukkit
import java.util.logging.Logger

/**
 * マクロコマンドを管理するマネージャー
 */
object MacroManager {
    private val logger: Logger = ResourceWorldManager.instance.logger

    /**
     * ワールド削除前のマクロを実行
     * プレースホルダー: %world_name%
     */
    fun executeBeforeDelete(worldName: String) {
        if (!ConfigManager.isMacroBeforeDeleteEnabled()) return

        val commands = ConfigManager.getMacroBeforeDeleteCommands()
        executeCommands(commands, worldName, "before_delete")
    }

    /**
     * ワールド生成完了後のマクロを実行
     * プレースホルダー: %world_name%, %border_size%
     */
    fun executeAfterGeneration(worldName: String, borderSize: Int) {
        if (!ConfigManager.isMacroAfterGenerationEnabled()) return

        val commands = ConfigManager.getMacroAfterGenerationCommands()
        executeCommands(commands, worldName, "after_generation", borderSize)
    }

    /**
     * 優先エリア生成完了後のマクロを実行
     * プレースホルダー: %world_name%
     */
    fun executeAfterPriorityPregen(worldName: String) {
        if (!ConfigManager.isMacroAfterPriorityPregenEnabled()) return

        val commands = ConfigManager.getMacroAfterPriorityPregenCommands()
        executeCommands(commands, worldName, "after_priority_pregen")
    }

    /**
     * 全エリア生成完了後のマクロを実行
     * プレースホルダー: %world_name%
     */
    fun executeAfterAllPregen(worldName: String) {
        if (!ConfigManager.isMacroAfterAllPregenEnabled()) return

        val commands = ConfigManager.getMacroAfterAllPregenCommands()
        executeCommands(commands, worldName, "after_all_pregen")
    }

    /**
     * コマンドを実行する
     */
    private fun executeCommands(commands: List<String>, worldName: String, macroType: String, borderSize: Int? = null) {
        if (commands.isEmpty()) return

        logger.info("マクロ [$macroType] を実行します (${commands.size}個のコマンド)")

        for (command in commands) {
            // プレースホルダーを置換
            var processedCommand = command.replace("%world_name%", worldName)
            if (borderSize != null) {
                processedCommand = processedCommand.replace("%border_size%", borderSize.toString())
            }

            try {
                // コンソールからコマンドを実行
                val success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                if (success) {
                    logger.info("マクロコマンドを実行しました: $processedCommand")
                } else {
                    logger.warning("マクロコマンドの実行に失敗しました: $processedCommand")
                }
            } catch (e: Exception) {
                logger.severe("マクロコマンド実行中にエラーが発生しました: $processedCommand")
                e.printStackTrace()
            }
        }
    }
}
