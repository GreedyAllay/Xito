package io.github.ultreon.bot.data

import com.google.gson.annotations.Expose
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.Channel
import io.github.ultreon.bot.gson
import io.github.ultreon.bot.utils.ChannelUsageType
import io.github.ultreon.bot.utils.RoleUsageType
import io.github.ultreon.bot.utils.saveToFile
import java.math.BigInteger

class ServerData(
  @field:Expose(serialize = false, deserialize = false)
  var guildId: Snowflake
) : Savable {
  override fun save() {
    gson.toJson(this)
      .saveToFile("guilds/${this.guildId}/data.json")
  }

  var description: String = "*No description set*"
  var accent: Int = 0xffffff
  var fields: MutableMap<String, String>? = null
  var topRankings: MutableMap<String, Pair<Int, Double>>? = mutableMapOf()
    get() = if (field == null) mutableMapOf<String, Pair<Int, Double>>().also { field = it } else field

  /**
   * Set of channel ids that should be ignored when leveling up.
   */
  var levelBlacklist: MutableSet<Snowflake> = mutableSetOf()

  val messages: MutableMap<String, ULong> = mutableMapOf()
  var channelTypes: MutableMap<ChannelUsageType, ULong> = mutableMapOf()
  var roleTypes: MutableMap<RoleUsageType, ULong> = mutableMapOf()

  var lastCountNumber: BigInteger = 0.toBigInteger()
  var highestCountNumber: BigInteger = 0.toBigInteger()

  var lastCountedMember: Snowflake? = null
  var lastCountMessageId: Snowflake? = null

  var quarantined: MutableSet<Snowflake> = mutableSetOf()

  fun isChannelBlacklistedForLeveling(channel: Snowflake): Boolean {
    return channel in levelBlacklist
  }

  fun isChannelBlacklistedForLeveling(channel: Channel): Boolean {
    return isChannelBlacklistedForLeveling(channel.id)
  }
}