package awabi2048.resourcegenerator

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ResourceCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) return false

        when (args[0].lowercase()) {
            "generate" -> {
                if (!hasPluginPermission(sender, "resource.generate")) {
                    sender.sendMessage("§c権限がありません。")
                    return true
                }

                if (args.size < 2) {
                    sender.sendMessage("§c使用法: /resource generate <type>:<variation> [border_size]")
                    return true
                }

                val target = args[1].split(":")
                if (target.size != 2) {
                    sender.sendMessage("§c形式が正しくありません (例: normal:a)")
                    return true
                }

                val type = target[0]
                val variation = target[1]
                val borderSize = if (args.size >= 3) args[2].toIntOrNull() else null

                sender.sendMessage("§e資源ワールド ${type}:${variation} の生成を開始します...")
                val success = WorldManager.generateResourceWorld(type, variation, borderSize)
                if (success) {
                    sender.sendMessage("§a資源ワールド ${type}:${variation} の生成に成功しました。")
                } else {
                    sender.sendMessage("§c資源ワールド ${type}:${variation} の生成に失敗しました。詳細はコンソールを確認してください。")
                }
                return true
            }

            "teleport" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cこのコマンドはプレイヤーのみ実行可能です。")
                    return true
                }

                if (!hasPluginPermission(sender, "resource.teleport")) {
                    sender.sendMessage("§c権限がありません。")
                    return true
                }

                if (args.size < 2) {
                    sender.sendMessage("§c使用法: /resource teleport <type>:<variation> [player]")
                    return true
                }

                val target = args[1].split(":")
                if (target.size != 2) {
                    sender.sendMessage("§c形式が正しくありません (例: normal:a)")
                    return true
                }

                val type = target[0]
                val variation = target[1]
                
                val targetPlayer = if (args.size >= 3) {
                    Bukkit.getPlayer(args[2]) ?: run {
                        sender.sendMessage("§cプレイヤー ${args[2]} は見つかりません。")
                        return true
                    }
                } else {
                    sender
                }

                WorldManager.teleportToResourceWorld(targetPlayer, type, variation)
                return true
            }

            "reload" -> {
                if (!hasPluginPermission(sender, "resource.reload")) {
                    sender.sendMessage("§c権限がありません。")
                    return true
                }
                ResourceGenerator.instance.reloadConfig()
                ConfigManager.load(ResourceGenerator.instance.config)
                sender.sendMessage("§a[ResourceGenerator] 設定を再読み込みしました。")
                return true
            }
        }

        return false
    }

    private fun hasPluginPermission(sender: CommandSender, permission: String): Boolean {
        return sender.hasPermission(permission) || sender.hasPermission("craftercrossing.test")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val list = mutableListOf<String>()

        if (args.size == 1) {
            val subCommands = mutableListOf<String>()
            if (hasPluginPermission(sender, "resource.generate")) subCommands.add("generate")
            if (hasPluginPermission(sender, "resource.teleport")) subCommands.add("teleport")
            if (hasPluginPermission(sender, "resource.reload")) subCommands.add("reload")
            
            list.addAll(subCommands.filter { it.startsWith(args[0].lowercase()) })
        } else if (args.size == 2) {
            val query = args[1].lowercase()
            for ((type, config) in ConfigManager.getAllResourceConfigs()) {
                for (variation in config.variations) {
                    val target = "$type:$variation"
                    if (target.startsWith(query)) {
                        list.add(target)
                    }
                }
            }
        } else if (args.size == 3 && args[0].lowercase() == "teleport") {
            if (hasPluginPermission(sender, "resource.teleport")) {
                list.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[2].lowercase()) })
            }
        }

        return list
    }
}
