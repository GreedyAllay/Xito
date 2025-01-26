package io.github.ultreon.bot.data

import dev.kord.common.entity.Snowflake
import io.github.ultreon.bot.gson

object ServerDataManager : CachedManager<Snowflake, ServerData>() {
  override fun create(key: Snowflake, secondary: Snowflake?): ServerData {
    val serverData = ServerData(key)
    serverData.save()
    return serverData
  }

  override fun path(key: Snowflake, secondary: Snowflake?): String {
    return "guilds/$key/data.json"
  }

  @Suppress("KotlinConstantConditions")
  override fun load(key: Snowflake, secondary: Snowflake?, data: String): ServerData {
    return gson.fromJson(data, ServerData::class.java).also {
      it.guildId = key
      if ((it.quarantined as Any?) == null) it.quarantined = mutableSetOf()
      if ((it.levelBlacklist as Any?) == null) it.levelBlacklist = mutableSetOf()
      if ((it.topRankings as Any?) == null) it.topRankings = mutableMapOf()
      if ((it.roleTypes as Any?) == null) it.roleTypes = mutableMapOf()
      if ((it.channelTypes as Any?) == null) it.channelTypes = mutableMapOf()
    }
  }
}