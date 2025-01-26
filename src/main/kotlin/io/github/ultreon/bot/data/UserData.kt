package io.github.ultreon.bot.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.kotlindiscord.kord.extensions.utils.isNullOrBot
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import io.github.ultreon.bot.gson
import kotlinx.datetime.Clock
import java.math.BigInteger
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt

import org.graalvm.polyglot.Value
fun calcRequiredLevelUp(level: Int) = (((level + 1) * (1 / 3.0) * PI + level * 3) * 13).pow(1.2).roundToInt()

class UserData(
  @Expose(serialize = false, deserialize = false) var id: Snowflake,

  @Expose(serialize = false, deserialize = false) var guildId: Snowflake
) : Savable {
  var variables: MutableMap<String, BigInteger>? = mutableMapOf()
    get() {
      if (field == null) {
        variables = mutableMapOf()
      }
      return field!!
    }
  var lastMessageTime = Clock.System.now()
  private val requiredForLevelUp get() = calcRequiredLevelUp(level)

  val serverData get() = ServerDataManager[guildId]

  @SerializedName("xp")
  var xp = 0.0
    set(value) {
      serverData.topRankings!![id.toString()] = level to value
      serverData.save()
      field = value
    }

  @SerializedName("level")
  var level = 0
    set(value) {
      serverData.topRankings!![id.toString()] = value to xp
      serverData.save()
      field = value
    }

  @SerializedName("coins")
  var coins = 0

  @SerializedName("countingGame")
  var countingGame = CountingGame()

  override fun save() {
    val json = gson.toJson(this)
    json.saveToFile("guilds/$guildId/members/$id.json")
  }

  suspend fun addXp(amount: Double, contextMsg: Message?) {
    if (amount == 0.0) return
    if (contextMsg != null && contextMsg.author.isNullOrBot()) return
    if (contextMsg != null && ServerDataManager[guildId].levelBlacklist.contains(contextMsg.channelId)) return

    xp += amount
    while (xp >= requiredForLevelUp) {
      xp -= requiredForLevelUp
      level++
      contextMsg?.respond(pingInReply = false) {
        content = "You have reached level $level!"
      }
    }
  }

  suspend fun addCoins(amount: Int) {
    coins += amount
  }

  class CountingGame {
    @SerializedName("highest")
    var highest: Int = 0

    @SerializedName("lowest")
    var lowest: Int = 0

    @SerializedName("average")
    var average: Int = 0

    @SerializedName("total")
    var total: Int = 0

    fun reset() {
      highest = 0
      lowest = 0
      average = 0
      total = 0
    }
  }

}

private fun String.saveToFile(s: String) {
  Paths.get(s)
    .createParentDirectories()
    .writeText(this)
}
