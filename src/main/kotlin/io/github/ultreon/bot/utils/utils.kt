package io.github.ultreon.bot.utils

import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import dev.kord.common.Color
import dev.kord.common.entity.DiscordGuild
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.interaction.followup.PublicFollowupMessageBehavior
import dev.kord.core.entity.Guild
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.ServerData
import io.github.ultreon.bot.data.ServerDataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration

fun String.saveToFile(s: String) {
  Paths.get(s).createParentDirectories().writeText(this)
}

fun readFile(path: String): String {
  try {
    return Paths.get(path).toFile().readText()
  } catch (_: Exception) {
    println("Failed to read file: $path")
    exitProcess(1)
  }
}

fun fileExists(path: String): Boolean {
  return Paths.get(path).toFile().exists()
}

val ULong.snowflake: Snowflake get() = Snowflake(this)

suspend fun MessageCreateBuilder.createInfoMessage(guild: GuildBehavior?) {
  if (guild == null) {
    println("Guild is null")
    return
  }
  return embed {
    val serverData = ServerDataManager[guild.id]

    title = guild.asGuild().name
    description = serverData.description

    color = Color(serverData.accent)

    for ((k, v) in serverData.fields ?: emptyMap()) {
      field {
        name = k
        value = v
      }
    }
  }
}

fun PublicFollowupMessageBehavior.deleteAfter(duration: Duration) {
  this.kord.launch {
    delay(duration)
    delete()
  }
}

val PublicSlashCommandContext<*, *>.serverData: ServerData
  get() = ServerDataManager[this.guild!!.id]
