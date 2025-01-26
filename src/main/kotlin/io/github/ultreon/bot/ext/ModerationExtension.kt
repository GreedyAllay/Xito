package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.isEphemeral
import com.kotlindiscord.kord.extensions.utils.toDuration
import dev.kord.common.Color
import dev.kord.common.entity.*
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import dev.kord.rest.route.Route
import io.github.ultreon.bot.utils.ChannelUsageType
import io.github.ultreon.bot.utils.deleteAfter
import io.github.ultreon.bot.utils.serverData
import io.github.ultreon.bot.utils.snowflake
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toDateTimePeriod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ModerationExtension : Extension() {
  override val name = "moderation"

  override suspend fun setup() {
    publicSlashCommand {
      name = "moderation"
      description = "Moderation commands."

      check { hasPermission(Permission.ManageMessages) }

      this.publicSubCommand(::PurgeArgs) {
        name = "purge"
        description = "Purge messages."

        action command@{
          respond {
            content = "Are you sure you want to purge ${arguments.amount} messages?"
            components {
              actionRow {
                this.interactionButton(
                  ButtonStyle.Danger,
                  "purge:${arguments.amount}:${event.interaction.user.id}"
                ) {
                  label = "Purge"
                  style = ButtonStyle.Danger
                }

                interactionButton(ButtonStyle.Secondary, "cancel:${event.interaction.user.id}") {
                  label = "Cancel"
                  style = ButtonStyle.Secondary
                }
              }
            }
          }
        }
      }

      this.publicSubCommand(::KickArgs) {
        name = "kick"
        description = "Kick a user."

        check { hasPermission(Permission.KickMembers) }

        action {

          val user = arguments.user

          val member = guild?.getMember(user.id) ?: run {
            respond {
              content = "User not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          member.kick(reason = arguments.reason)

          val message = respond {
            content = "Kicked ${user.mention}!"
          }

          if (arguments.deleteMessage) {
            message.deleteAfter(5.seconds)
          }

          val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: return@action
          val logChannel =
            guild?.getChannelOfOrNull<TextChannel>(logsId.snowflake)
          logChannel?.createMessage {
            embed {
              title = "User was kicked!"
              description = "User ${user.mention} was kicked by ${event.interaction.user.mention}!"
              field {
                name = "Reason"
                value = arguments.reason ?: "*No reason provided.*"
              }
            }
          }
        }
      }

      this.publicSubCommand(::BanArgs) {
        name = "ban"
        description = "Ban a user."

        check { hasPermission(Permission.BanMembers) }

        action {
          val user = arguments.user

          guild?.ban(user.id) {
            reason = arguments.reason
            deleteMessageDuration =
              arguments.deleteMessagesDuration.toDuration(TimeZone.currentSystemDefault())
          } ?: run {
            respond {
              content = "This command can only be used in servers."
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          val message = respond {
            content = "Banned ${user.mention}!"

            if (arguments.silent) flags = MessageFlags(MessageFlag.Ephemeral)
          }

          if (arguments.deleteMessage && !arguments.silent)
            message.deleteAfter(5.seconds)
          val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: return@action
          val logs = guild?.getChannelOfOrNull<TextChannel>(logsId.snowflake)
            ?: run {
              respond {
                content = "Logs channel not found!"
                flags = MessageFlags(MessageFlag.Ephemeral)
              }
              return@action
            }

          logs.createMessage {
            embed {
              title = "User was banned!"
              description = "The user ${user.mention} was banned!"
              color = Color(0xff0000)

              field {
                name = "Reason"
                value = arguments.reason ?: "*No reason provided*"
                inline = true
              }

              field {
                name = "Responsible Moderator"
                value = event.interaction.user.mention
                inline = true
              }
            }
          }
        }
      }

      this.publicSubCommand(::UnbanArgs) {
        name = "unban"
        description = "Unban a user."

        check { hasPermission(Permission.BanMembers) }

        action {
          val user = arguments.user

          guild?.unban(user.id, reason = arguments.reason) ?: run {
            respond {
              content = "This command can only be used in servers."
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          val message = respond {
            content = "Unbanned ${user.mention}!"
          }

          if (arguments.deleteMessage) message.deleteAfter(5.seconds)

          val logsId = serverData.channelTypes[ChannelUsageType.LOGS] ?: return@action
          val logs = guild?.getChannelOfOrNull<TextChannel>(logsId.snowflake)
            ?: run {
              respond {
                content = "Logs channel not found!"
                flags = MessageFlags(MessageFlag.Ephemeral)
              }
              return@action
            }

          logs.createMessage {
            embed {
              title = "User was unbanned!"
              description = "The user ${user.effectiveName} was unbanned!"
              color = Color(0x00ff00)
            }
          }
        }
      }

      this.publicSubCommand(::LockdownArgs) {
        name = "lockdown"
        description = "Lockdown the server."

        check { hasPermission(Permission.ManageGuild) }

        action {
          val lockdownChannel = guild?.getChannelOfOrNull<TextChannel>(serverData.channelTypes[ChannelUsageType.LOCKDOWN]?.snowflake ?: run {
            respond {
              content = "Lockdown channel not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }) ?: run {
            respond {
              content = "Lockdown channel not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          val everyoneRole = guild?.roles?.firstOrNull { it.name == "@everyone" } ?: run {
            respond {
              content = "Everyone role not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          lockdownChannel.edit {
            topic = "Server is now in lockdown mode."
            permissionOverwrites = mutableSetOf(Overwrite(everyoneRole.id, OverwriteType.Role, Permissions(Permission.ViewChannel), Permissions(listOf<Permission>())))
          }

          lockdownChannel.createMessage {
            embed {
              title = "Server is now in lockdown mode!"
              description = arguments.reason ?: "No reason provided."
              color = Color(0xff0000)
              timestamp = Clock.System.now()

              footer {
                text = "Lockdown mode started by ${event.interaction.user.mention}"
                icon = event.interaction.user.avatar?.let {
                  if (it.isAnimated) {
                    it.cdnUrl.toUrl {
                      format = Image.Format.GIF
                    }
                  } else {
                    it.cdnUrl.toUrl()
                  }
                } ?: event.interaction.user.defaultAvatar.let {
                  if (it.isAnimated) {
                    it.cdnUrl.toUrl {
                      format = Image.Format.GIF
                    }
                  } else {
                    it.cdnUrl.toUrl()
                  }
                }
              }
            }
          }

          everyoneRole.edit {
            permissions = everyoneRole.permissions.minus(Permissions(Permission.ViewChannel))
          }

          respond {
            content = "Server is now in lockdown mode."
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
        }
      }
    }

    // Interaction handler for the purge button
    event<ButtonInteractionCreateEvent> {
      check { hasPermission(Permission.ManageMessages) }
      action {
        val parts = event.interaction.component.customId?.split(":") ?: run {
          event.interaction.respondEphemeral {
            content = "Invalid interaction."
          }
          return@action
        }
        if (parts[0] == "purge") {
          val amount = parts[1].toInt()
          if (amount <= 0) {
            event.interaction.respondEphemeral {
              content = "Invalid amount."
            }
            return@action
          }
          if (amount > 100) {
            event.interaction.respondEphemeral {
              content = "You can only purge up to 100 messages."
            }
            return@action
          }
          val user = kord.getUser(parts[2].toULong().snowflake)

          if (user?.id != event.interaction.user.id) {
            event.interaction.respondEphemeral {
              content = "You can only purge messages if you used the purge command!"
            }
            return@action
          }

          val response = event.interaction.deferEphemeralResponse()
          event.interaction.message.edit {
            content = "Purging messages..."
          }
          val channel = event.interaction.channel.fetchChannel()
          if (channel !is TextChannelBehavior) {
            event.interaction.message.edit {
              content = "Purging messages failed."
            }
            return@action
          }

          if (channel.lastMessage == null) {
            event.interaction.message.edit {
              content = "Purging messages failed."
              components = mutableListOf()
            }

            response.respond {
              content = "Purging messages failed: No last message."
            }

            kord.launch {
              delay(2.seconds)
              event.interaction.message.delete()
            }

            return@action
          }

          val foundMessages = channel.getMessagesBefore(channel.getLastMessage()?.id ?: run {
            event.interaction.message.edit {
              content = "Purging messages failed."
              components = mutableListOf()
            }

            response.respond {
              content = "Purging messages failed: No last message."
            }

            kord.launch {
              delay(2.seconds)
              event.interaction.message.delete()
            }

            return@action
          })
            .filter { it.id != event.interaction.message.id && !it.isEphemeral }
            .take(amount)
            .toList()

          if (foundMessages.isEmpty()) {
            event.interaction.message.edit {
              content = "Purging messages failed."
              components = mutableListOf()
            }

            response.respond {
              content = "Purging messages failed: No messages found."
            }

            kord.launch {
              delay(2.seconds)
              event.interaction.message.delete()
            }

            return@action
          }

          foundMessages.forEach {
            it.delete()
          }

          response.respond {
            content = "Purged ${foundMessages.size} messages in ${channel.mention}."
          }

          // Schedule a task to delete the message
          kord.launch {
            delay(2.seconds)
            event.interaction.message.delete()
          }
        } else if (parts[0] == "cancel") {
          if (parts[1].toULong().snowflake != event.interaction.user.id) {
            event.interaction.respondEphemeral {
              content = "You can only cancel the purge if you used the purge command!"
            }
            return@action
          }

          event.interaction.message.edit {
            content = "Cancelled."
            components = mutableListOf()
          }

          // Schedule a task to delete the message
          kord.launch {
            delay(2.seconds)
            event.interaction.message.delete()
          }
        } else {
          event.interaction.respondEphemeral {
            content = "Invalid button: " + event.interaction.component.customId
          }
        }
      }
    }
  }

  inner class LockdownArgs : Arguments() {
    val reason by optionalString {
      name = "reason"
      description = "The reason for the lockdown."
    }
  }

  inner class PurgeArgs : Arguments() {
    val amount by int {
      name = "amount"
      description = "The amount of messages to purge."
    }
  }

  inner class KickArgs : Arguments() {
    val user by user {
      name = "user"
      description = "The user to kick."
    }

    val reason by optionalString {
      name = "reason"
      description = "The reason for the kick."
    }

    val deleteMessage by defaultingBoolean {
      name = "delete-message"
      description = "Whether to delete the command message."

      defaultValue = true
    }
  }

  inner class BanArgs : Arguments() {
    val user by user {
      name = "user"
      description = "The user to ban."
    }

    val reason by optionalString {
      name = "reason"
      description = "The reason for the ban."
    }

    val deleteMessage by defaultingBoolean {
      name = "delete-message"
      description = "Whether to delete the command message."

      defaultValue = true
    }

    val silent by defaultingBoolean {
      name = "silent"
      description = "Whether to send the ban message privately or publicly."

      defaultValue = false
    }

    val deleteMessagesDuration by defaultingDuration {
      name = "delete-history"
      description = "The amount of history to delete in duration."

      defaultValue = Duration.ZERO.toDateTimePeriod()
    }
  }

  inner class UnbanArgs : Arguments() {
    val user by user {
      name = "user"
      description = "The user to unban."
    }

    val reason by optionalString {
      name = "reason"
      description = "The reason for the unban."
    }

    val deleteMessage by defaultingBoolean {
      name = "delete-message"
      description = "Whether to delete the command message."

      defaultValue = true
    }
  }
}