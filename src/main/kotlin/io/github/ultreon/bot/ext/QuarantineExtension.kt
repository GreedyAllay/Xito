package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.ServerDataManager
import io.github.ultreon.bot.utils.ChannelUsageType
import io.github.ultreon.bot.utils.RoleUsageType
import io.github.ultreon.bot.utils.UserArgs
import io.github.ultreon.bot.utils.snowflake

class QuarantineExtension : Extension() {
  override val name = "quarantine"

  override suspend fun setup() {
    this.event<MemberJoinEvent> {
      action {
        val serverData = ServerDataManager[event.guildId]
        if (event.member.id in serverData.quarantined) {
          serverData.roleTypes[RoleUsageType.QUARANTINED]
            ?.let { role -> event.member.addRole(role.snowflake, reason = "User was quarantined!") }

          val channelId = serverData.channelTypes[ChannelUsageType.LOGS] ?: return@action
          val logChannel =
            event.guild.getChannelOfOrNull<TextChannel>(channelId.snowflake)
          logChannel?.createMessage {
            embed {
              title = "User was quarantined!"
              description = "User ${event.member.mention} was quarantined!"

              field {
                name = "Reason"
                value = "Automatic quarantine due to being in the quarantine list."
              }
            }
          }
        }
      }
    }

    this.event<MemberUpdateEvent> {
      action {
        val serverData = ServerDataManager[event.guildId]
        if (event.old?.roleIds != event.member.roleIds) {
          if (event.member.id in serverData.quarantined) {
            event.member.edit {
              roles?.let {
                for (role in it) {
                  if (role != serverData.roleTypes[RoleUsageType.QUARANTINED]!!.snowflake)
                    it.remove(role)
                }

                it.add(
                  serverData.roleTypes[RoleUsageType.QUARANTINED]!!.snowflake,
                )

                this.reason = "User was quarantined!"
              } ?: run {

              }
            }
            serverData.roleTypes[RoleUsageType.QUARANTINED]
              ?.let { role -> event.member.addRole(role.snowflake, reason = "User was quarantined!") }
          }
        }
      }
    }

    publicSlashCommand(::UserArgs) {
      name = "quarantine"
      description = "Quarantine a user."

      check { hasPermission(Permission.BanMembers) }

      action {
        val serverData = ServerDataManager[guild?.id ?: return@action]
        val user = arguments.user ?: run {
          respond { content = "User not found." }
          return@action
        }
        val userId = user.id
        val roleId = serverData.roleTypes[RoleUsageType.QUARANTINED]?.snowflake ?: run {
          respond { content = "Quarantine role not configured." }
          return@action
        }

        if (this.guild == null) {
          respond { content = "Not in a server." }
          return@action
        }

        val guild = this.guild

        if (userId in serverData.quarantined) {
          respond { content = "User is already quarantined." }
          return@action
        }

        // Check if the user is a member of the server
        val member = (guild ?: run {
          respond { content = "Not in a server." }
          return@action
        }).getMemberOrNull(userId)
        if (member != null) {
          // Give the user a quarantine role
          member.edit {
            roles?.let { roles ->
              roles.removeIf { it != roleId }
              roles.add(roleId)
            } ?: run {
              roles = mutableSetOf(roleId)
            }
          }
          serverData.quarantined.add(userId)
          serverData.save()

          val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: run {
            respond { content = "Logs channel not configured." }
            return@action
          }
          val logChannel =
            this.guild
              ?.getChannelOfOrNull<TextChannel>(logsId.snowflake)
          logChannel?.createMessage {
            embed {
              title = "Member quarantined!"
              description = "User ${user.mention} was quarantined!"
            }
          }

          respond { content = "User quarantined: ${user.effectiveName}." }
        } else {
          serverData.quarantined.add(userId)

          val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: run {
            respond { content = "Logs channel not configured." }
            return@action
          }
          val logChannel =
            guild.getChannelOfOrNull<TextChannel>(logsId.snowflake)
          logChannel?.createMessage {
            embed {
              title = "User quarantined!"
              description = "User ${user.effectiveName} was quarantined!"
            }
          }
          respond { content = "User pre-quarantined: ${user.effectiveName}" }
        }
      }
    }

    publicSlashCommand(::UserArgs) {
      name = "unquarantine"
      description = "Unquarantine a user."

      check { hasPermission(Permission.ManageGuild) }

      action {
        val serverData = ServerDataManager[guild?.id ?: return@action]
        val user = arguments.user ?: run {
          respond { content = "User not found." }
          return@action
        }
        val userId = user.id

        val guild = this.guild ?: run {
          respond {
            content = "Not in a server."
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
          return@action
        }

        if (userId !in serverData.quarantined) {
          respond { content = "User isn't quarantined." }
          return@action
        }

        val member = guild.getMemberOrNull(userId) ?: run {
          respond { content = "User not in server." }
          return@action
        }
        val roleId = serverData.roleTypes[RoleUsageType.QUARANTINED] ?: run {
          respond {
            content = "Quarantine role not configured."
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
          return@action
        }

        // Remove the quarantine role
        serverData.quarantined.remove(userId)
        serverData.save()

        member.removeRole(roleId.snowflake)

        // Create a log message
        val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: run {
          respond {
            content = "Log channel not configured."
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
          return@action
        }

        val logChannel = guild.getChannelOfOrNull<TextChannel>(logsId.snowflake)
        logChannel?.createMessage {
          embed {
            title = "User unquarantined!"
            description = "User ${user.mention} was unquarantined!"
          }
        } ?: run {
          respond {
            content = "Log channel not found."
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
          return@action
        }

        respond { content = "User unquarantined: ${user.effectiveName}." }
      }
    }
  }

}