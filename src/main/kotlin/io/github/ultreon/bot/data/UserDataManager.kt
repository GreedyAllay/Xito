package io.github.ultreon.bot.data

import dev.kord.common.entity.Snowflake
import io.github.ultreon.bot.gson

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
object UserDataManager : CachedManager<Snowflake, UserData>() {
  override fun path(guildId: Snowflake, userId: Snowflake?): String {
    if (userId == null) throw IllegalArgumentException("Secondary key cannot be null.")
    return "guilds/$guildId/members/$userId.json"
  }

    override fun create(guildId: Snowflake, userId: Snowflake?): UserData {
    if (userId == null) throw IllegalArgumentException("Secondary key cannot be null.")
    return UserData(userId, guildId)
  }

  override fun load(guildId: Snowflake, userId: Snowflake?, data: String): UserData {
    if (userId == null) throw IllegalArgumentException("Secondary key cannot be null.")
    return gson.fromJson(data, UserData::class.java).also {
      it.id = userId
      it.guildId = guildId
    }
  }

}
