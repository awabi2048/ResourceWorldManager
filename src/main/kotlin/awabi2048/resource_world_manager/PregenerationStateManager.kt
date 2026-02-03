package awabi2048.resource_world_manager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * 事前生成の進捗状態を管理するオブジェクト
 */
object PregenerationStateManager {
    private val gson = Gson()
    private val stateFile: File
        get() = File(ResourceWorldManager.instance.dataFolder, "pregeneration_state.json")

    private var states = mutableMapOf<String, PregenState>()

    /**
     * 事前生成の状態
     */
    data class PregenState(
        val worldName: String,
        val borderSize: Int,
        var currentIndex: Int = 0,
        var priorityCompleted: Boolean = false,
        var allCompleted: Boolean = false
    )

    /**
     * 状態をファイルから読み込む
     */
    fun load() {
        if (!stateFile.exists()) {
            states.clear()
            return
        }

        try {
            FileReader(stateFile).use { reader ->
                val type = object : TypeToken<MutableMap<String, PregenState>>() {}.type
                val loadedStates = gson.fromJson(reader, type) ?: mutableMapOf<String, PregenState>()
                states.clear()
                states.putAll(loadedStates)
                Bukkit.getLogger().info("事前生成の状態を読み込みました: ${states.size} 件")
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("事前生成の状態読み込みに失敗しました: ${e.message}")
            states.clear()
        }
    }

    /**
     * 状態をファイルに保存する
     */
    fun save() {
        try {
            stateFile.parentFile?.mkdirs()
            FileWriter(stateFile).use { writer ->
                gson.toJson(states, writer)
            }
        } catch (e: Exception) {
            Bukkit.getLogger().warning("事前生成の状態保存に失敗しました: ${e.message}")
        }
    }

    /**
     * 全ての状態をクリアする
     */
    fun clear() {
        states.clear()
        if (stateFile.exists()) {
            stateFile.delete()
        }
    }

    /**
     * 指定されたワールドの状態を削除する
     */
    fun remove(worldName: String) {
        states.remove(worldName)
        save()
    }

    /**
     * 状態を取得する
     */
    fun getState(worldName: String): PregenState? = states[worldName]

    /**
     * 状態を設定する
     */
    fun setState(state: PregenState) {
        states[state.worldName] = state
    }

    /**
     * 全ての状態を取得する
     */
    fun getAllStates(): Map<String, PregenState> = states.toMap()

    /**
     * 指定されたワールドの状態を更新する
     */
    fun updateState(worldName: String, updater: (PregenState) -> Unit) {
        states[worldName]?.let { state ->
            updater(state)
        }
    }
}
