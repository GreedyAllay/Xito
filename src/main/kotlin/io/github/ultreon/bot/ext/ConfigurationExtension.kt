@file:Suppress("t")

package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.hasPermission
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import dev.kord.common.Color
import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.*
import dev.kord.core.behavior.channel.threads.ThreadChannelBehavior
import dev.kord.core.behavior.channel.threads.edit
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.suggest
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.ServerDataManager
import io.github.ultreon.bot.utils.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.util.*
import kotlin.time.Duration

class ConfigurationExtension : Extension() {
  override val name = "configuration"

  override suspend fun setup() {
    publicSlashCommand {
      name = "configuration"
      description = "Configure the server."

      publicSubCommand(::ServerPropertiesArgs) {
        name = "server"
        description = "Configure server properties."

        check { hasPermission(Permission.ManageGuild) }

        action {
          val guild = requireNotNull(guild)
          val serverData = ServerDataManager[guild.id]
          val property = requireNotNull(arguments.property)

          when (property) {
            "fields" -> {
              if (arguments.operation == "put") {
                serverData.fields?.set(arguments.key ?: run {
                  respond {
                    content = "Must provide a key."
                  }
                  return@action
                }, arguments.value ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                }) ?: run {
                  serverData.fields = mutableMapOf((arguments.key ?: run {
                    respond {
                      content = "Must provide a key."
                    }
                    return@action
                  }) to (arguments.value ?: run {
                    respond {
                      content = "Must provide a value."
                    }
                    return@action
                  }))
                }

                respond {
                  content = "Set field `${arguments.key}` to `${arguments.value}`."
                }
              } else if (arguments.operation == "remove") {
                serverData.fields?.remove(arguments.key ?: run {
                  respond {
                    content = "Must provide a key."
                  }
                  return@action
                })

                respond {
                  content = "Removed field `${arguments.key}`."
                }
              } else if (arguments.operation == "get") {
                if (arguments.key == null) {
                  respond {
                    content = "Must provide a key."
                  }
                  return@action
                }
                respond {
                  content = "Field `${arguments.key}` is `${serverData.fields?.get(arguments.key)}`."
                }
              } else {
                respond {
                  content = "Can't ${arguments.operation} field `${arguments.key}`."
                }
              }
            }

            "description" -> {
              if (arguments.operation == "set") {
                serverData.description = arguments.value ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                }

                respond {
                  content = "Set description to `${arguments.value}`."
                }
              } else if (arguments.operation == "reset") {
                serverData.description = "*No description set*"

                respond {
                  content = "Reset description."
                }
              } else if (arguments.operation == "get") {
                respond {
                  content = "Description is `${serverData.description}`."
                }
              } else {
                respond {
                  content = "Can't ${arguments.operation} description."
                }
              }
            }

            "accent" -> {
              when (arguments.operation) {
                "set" -> {
                  try {
                    serverData.accent = arguments.value?.stringToColor() ?: run {
                      respond {
                        content = "Must provide a value."
                      }
                      return@action
                    }
                  } catch (e: IllegalArgumentException) {
                    respond {
                      content = e.message
                    }
                    return@action
                  }

                  respond {
                    content = "Set accent to `#${serverData.accent.toString(16).padStart(6, '0')}`."
                  }
                }

                "reset" -> {
                  serverData.accent = 0xffffff
                  respond {
                    content = "Reset accent to `#${serverData.accent.toString(16).padStart(6, '0')}`."
                  }
                }

                "random" -> {
                  serverData.accent = Random().nextInt(0xffffff)

                  respond {
                    content = "Randomized accent to `#${serverData.accent.toString(16).padStart(6, '0')}`."
                  }
                }

                "get" -> {
                  respond {
                    content = "Accent is `#${serverData.accent.toString(16).padStart(6, '0')}`."
                  }
                }

                else -> {
                  respond {
                    content = "Can't ${arguments.operation} accent."
                  }
                }
              }
            }

            "level-blacklist" -> {
              if (arguments.operation == "add") {
                serverData.levelBlacklist.add(arguments.value?.toULong()?.snowflake ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                })

                respond {
                  content = "Added `${arguments.value?.toULong()?.snowflake!!}` to the level blacklist."
                }
              } else if (arguments.operation == "remove") {
                serverData.levelBlacklist.remove(arguments.value?.toULong()?.snowflake ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                })

                respond {
                  content = "Removed `${arguments.value?.toULong()?.snowflake!!}` from the level blacklist."
                }
              }
            }

            "quarantine" -> {
              if (arguments.operation == "add") {
                serverData.quarantined.add(arguments.key?.toULong()?.snowflake ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                })

                respond {
                  content = "Added `${arguments.key?.toULong()?.snowflake!!}` to the quarantine list."
                }
              } else if (arguments.operation == "remove") {
                serverData.quarantined.remove(arguments.key?.toULong()?.snowflake ?: run {
                  respond {
                    content = "Must provide a value."
                  }
                  return@action
                })

                respond {
                  content = "Removed `${arguments.key?.toULong()?.snowflake!!}` from the quarantine list."
                }
              }
            }

            else -> {
              respond {
                content = "Invalid property."
              }
            }
          }
        }
      }
      publicSubCommand(::ConfigureArgs) {
        name = "channel"
        description = "Configure a channel."

        check { hasPermission(Permission.ManageGuild) }

        action {
          val type = requireNotNull(arguments.type)
          val id = arguments.key.toULong().snowflake
          when (type) {
            "channel" -> {
              if (id == guild?.id) {
                when (arguments.property) {
                  "name" -> {
                    guild?.edit { name = arguments.value }
                  }

                  "verification-level" -> {
                    guild?.edit {
                      verificationLevel = when (arguments.value) {
                        "none" -> VerificationLevel.None
                        "low" -> VerificationLevel.Low
                        "medium" -> VerificationLevel.Medium
                        "high" -> VerificationLevel.High
                        "very-high" -> VerificationLevel.VeryHigh
                        else -> {
                          respond { content = "Invalid verification level." }
                          return@action
                        }
                      }
                    }
                  }

                  "default-message-notification-level" -> {
                    guild?.edit {
                      notificationLevel = when (arguments.value) {
                        "all-messages" -> DefaultMessageNotificationLevel.AllMessages
                        "only-mentions" -> DefaultMessageNotificationLevel.OnlyMentions
                        else -> {
                          respond { content = "Invalid default message notification level." }
                          return@action
                        }
                      }
                    }
                  }

                  "explicit-content-filter" -> {
                    guild?.edit {
                      explicitContentFilter = when (arguments.value) {
                        "disabled" -> ExplicitContentFilter.Disabled
                        "members-without-roles" -> ExplicitContentFilter.MembersWithoutRoles
                        "all-members" -> ExplicitContentFilter.AllMembers
                        else -> {
                          respond { content = "Invalid explicit content filter." }
                          return@action
                        }
                      }
                    }
                  }

                  "afk-timeout" -> {
                    guild?.edit {
                      afkTimeout = Duration.parse(arguments.value).also {
                        if (it <= Duration.ZERO) {
                          respond { content = "AFK timeout must be greater than zero." }
                          return@action
                        }
                      }
                    }
                  }

                  else -> {
                    respond { content = "Invalid property to modify." }
                  }
                }

                return@action
              }
              val channel = guild?.getChannel(id) ?: run {
                respond {
                  embed {
                    title = "Channel not found"
                    description = "The channel you specified does not exist."
                    color = Color(255, 32, 48)
                  }
                }
                return@action
              }
              val property = arguments.property
              val value = arguments.value

              when (property) {
                "name" -> {
                  when (channel) {
                    is TextChannelBehavior -> channel.edit { name = value.replace(' ', '-') }
                    is VoiceChannelBehavior -> channel.edit { name = value }
                    is ThreadChannelBehavior -> channel.edit { name = value }
                    is ForumChannelBehavior -> channel.edit { name = value }
                    is MediaChannelBehavior -> channel.edit { name = value }
                    is StageChannelBehavior -> channel.edit { name = value }
                    else -> {
                      respond { content = "Invalid channel to modify the name of." }
                      return@action
                    }
                  }
                }

                "topic" -> {
                  when (channel) {
                    is TextChannelBehavior -> channel.edit { topic = value }
                    is VoiceChannelBehavior -> channel.edit { topic = value }
                    is ForumChannelBehavior -> channel.edit { topic = value }
                    is MediaChannelBehavior -> channel.edit { topic = value }
                    is StageChannelBehavior -> channel.edit { topic = value }
                    else -> {
                      respond {
                        content = "Invalid channel to modify the topic of."
                        flags = MessageFlags(MessageFlag.Ephemeral)
                      }
                      return@action
                    }
                  }
                }

                "type" -> {
                  if (value !in ChannelUsageType.entries.map { it.name }) {
                    respond {
                      content = "Invalid channel type: `${value}`."
                      flags = MessageFlags(MessageFlag.Ephemeral)
                    }
                    return@action
                  }

                  when (ChannelUsageType.valueOf(value)) {
                    ChannelUsageType.INFO -> {
                      if (channel !is TextChannelBehavior) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.INFO.displayName}`."
                        }
                        return@action
                      }
                      val createMessage = channel.createMessage { createInfoMessage(guild) }

                      serverData.channelTypes[ChannelUsageType.valueOf(value)] = channel.id.value
                      serverData.messages[MessageReferences.INFO] = createMessage.id.value
                      serverData.save()

                      val guildId = channel.guildId
                      channel.addOverwrite(
                        PermissionOverwrite.forEveryone(
                          guildId,
                          Permissions(Permission.ViewChannel),
                          Permissions(
                            Permission.SendMessages,
                            Permission.SendMessagesInThreads,
                            Permission.CreatePublicThreads,
                            Permission.CreatePrivateThreads
                          )
                        ), reason = "Channel Type updated to $value"
                      )

                      return@action
                    }

                    ChannelUsageType.WELCOME -> {
                      when (channel) {
                        is TextChannelBehavior -> channel.edit {
                          topic =
                            "Welcome to the ${channel.guild.asGuild().name} server!"; reason =
                          "Channel Type updated to $value"
                        }

                        is ForumChannelBehavior -> channel.edit {
                          topic =
                            "Welcome to the ${channel.guild.asGuild().name} server!"; reason =
                          "Channel Type updated to $value"
                        }

                        else -> {
                          respond {
                            content =
                              "Invalid channel to modify the type to `${ChannelUsageType.WELCOME.displayName}`."
                            flags = MessageFlags(MessageFlag.Ephemeral)
                          }

                          return@action
                        }
                      }

                      serverData.channelTypes[ChannelUsageType.WELCOME] = channel.id.value
                      serverData.save()
                    }

                    ChannelUsageType.COUNTING_GAME -> {
                      if (channel !is TextChannelBehavior && channel !is ThreadChannelBehavior) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.COUNTING_GAME.displayName}`."
                        }
                        return@action
                      }

                      serverData.channelTypes[ChannelUsageType.COUNTING_GAME] = channel.id.value
                      serverData.save()
                    }

                    ChannelUsageType.LOCKDOWN -> {
                      if (channel !is TextChannel) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.LOCKDOWN.displayName}`."
                        }
                        return@action
                      }

                      serverData.channelTypes[ChannelUsageType.LOCKDOWN] = channel.id.value
                      serverData.save()
                    }

                    ChannelUsageType.LOGS -> {
                      if (channel !is TextChannelBehavior) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.LOGS.displayName}`."
                        }
                        return@action
                      }

                      serverData.channelTypes[ChannelUsageType.LOGS] = channel.id.value
                      serverData.save()
                    }

                    ChannelUsageType.RULES -> {
                      if (channel !is TextChannelBehavior) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.RULES.displayName}`."
                        }
                        return@action
                      }

                      serverData.channelTypes[ChannelUsageType.RULES] = channel.id.value
                      serverData.save()
                    }

                    ChannelUsageType.PROJECT -> {
                      if (channel !is TextChannelBehavior) {
                        respond {
                          content =
                            "Invalid channel to modify the type to `${ChannelUsageType.PROJECT.displayName}`."
                        }
                        return@action
                      }

                      serverData.channelTypes[ChannelUsageType.PROJECT] = channel.id.value
                      serverData.save()
                    }

                    else -> {
                      respond {
                        content = "Not implemented yet! Sorry :("
                      }

                      return@action
                    }
                  }
                }
              }

              respond {
                content = "Successfully changed the `$property` of the channel to `$value`."
              }
            }

            "role" -> {
              val role: Role = guild!!.getRole(id)
              val property = arguments.property

              when (property) {
                "name" -> {
                  role.edit { name = arguments.value }

                  respond {
                    content = "Successfully changed the name of the role to `${arguments.value}`."
                  }
                }

                "color" -> {
                  val color = try {
                    val rgb: Int
                    if (!arguments.value.startsWith("#")) {
                      respond {
                        content = "Invalid color: `${arguments.value}`."
                        flags = MessageFlags(MessageFlag.Ephemeral)
                      }
                      return@action
                    } else if (arguments.value.length == 7) {
                      rgb = arguments.value.substring(1).toInt(16)
                    } else if (arguments.value.length == 4) {
                      val red = arguments.value[1].toString() + arguments.value[1]
                      val green = arguments.value[2].toString() + arguments.value[2]
                      val blue = arguments.value[3].toString() + arguments.value[3]
                      rgb = red.toInt(16) * 65536 + green.toInt(16) * 256 + blue.toInt(16)
                    } else {
                      respond {
                        content = "Invalid color: `${arguments.value}`."
                        flags = MessageFlags(MessageFlag.Ephemeral)
                      }
                      return@action
                    }
                    Color(rgb)
                  } catch (e: NumberFormatException) {
                    respond {
                      content = "Invalid color: `${arguments.value}`."
                      flags = MessageFlags(MessageFlag.Ephemeral)
                    }
                    return@action
                  }
                  role.edit { this@edit.color = color }

                  respond {
                    content = "Successfully changed the color of the role to `${arguments.value}`."
                  }

                  return@action
                }

                "type" -> {
                  val roleType = try {
                    RoleUsageType.valueOf(arguments.value.uppercase(Locale.getDefault()))
                  } catch (e: IllegalArgumentException) {
                    respond {
                      content = "Invalid role type: `${arguments.value}`."
                      flags = MessageFlags(MessageFlag.Ephemeral)
                    }
                    return@action
                  }

                  serverData.roleTypes[roleType] = role.id.value
                  serverData.save()

                  respond {
                    content =
                      "Successfully changed the role type of the role to `${arguments.value}`."
                  }
                }

                else -> {
                  respond {
                    content = "Not implemented yet! Sorry :("
                  }
                  return@action
                }
              }
            }
          }
        }
      }
    }
  }

  inner class ServerPropertiesArgs : Arguments() {
    val property by stringChoice {
      name = "property"
      description = "The property to configure."

      choice("Description", "description")
      choice("Accent", "accent")
    }

    val operation by stringChoice {
      name = "operation"
      description = "The operation to perform."

      autoComplete {
        val ch = this.channel
        val guild = if (ch is GuildChannelBehavior) ch.guildId else null
        val serverData = ServerDataManager[guild ?: return@autoComplete]

        this.command.strings["property"]?.let { property ->
          when (property) {
            "fields" -> {
              this@autoComplete.suggestString {
                listOf("put", "remove", "get", "reset").forEach {
                  this@suggestString.choice(it[0].uppercase() + it.substring(1), it)
                }
              }
            }

            "accent" -> {
              this@autoComplete.suggestString {
                listOf("set", "get", "reset").forEach {
                  this@suggestString.choice(it[0].uppercase() + it.substring(1), it)
                }
              }
            }

            "name" -> {
              this@autoComplete.suggestString {
                listOf("set", "get", "reset").forEach {
                  this@suggestString.choice(it[0].uppercase() + it.substring(1), it)
                }
              }
            }

            else -> {
              this@autoComplete.suggestString {

              }
            }
          }
        }
      }
    }

    val key by optionalString {
      name = "key"
      description = "The key to set the property to (only works with 'put' and 'remove' operations)."

      autoComplete {
        val ch = this.channel
        val guild = if (ch is GuildChannelBehavior) ch.guildId else null
        val serverData = ServerDataManager[guild ?: return@autoComplete]

        this.command.strings["property"]?.let { property ->
          when (property) {
            "fields" -> {
              when (this.command.strings["operation"]) {
                "remove" -> {
                  this.run {
                    val current = this.focusedOption.value

                    // Loop through current field keys
                    serverData.fields?.keys
                      ?.filter { it.lowercase().startsWith(current.lowercase()) }
                      ?.toList()
                      ?.take(25)
                      ?.toMutableList()
                      ?.also {
                        this@autoComplete.suggestString {
                          for (entry in it) {
                            this@suggestString.choice(entry, entry)
                          }
                        }
                      } ?: this@autoComplete.suggestString { }
                  }
                }
              }
            }
          }
        }
      }
    }

    val value by optionalString {
      name = "value"
      description = "The value to set the property to."

      autoComplete {
        val ch = this.channel
        val guild = if (ch is GuildChannelBehavior) ch.guildId else null
        val serverData = ServerDataManager[guild ?: return@autoComplete]

        this.command.strings["property"]?.let { property ->
          when (property) {
            "accent" -> {
              if (this.command.strings["operation"] != "set") {
                return@autoComplete
              }
              this@autoComplete.suggestString {
                listOf(
                  "Red" to "#FF0000",
                  "Orange" to "#FFA500",
                  "Yellow" to "#FFFF00",
                  "Green" to "#008000",
                  "Blue" to "#0000FF",
                  "Purple" to "#800080",
                  "Pink" to "#FFC0CB",
                  "Black" to "#000000",
                  "White" to "#FFFFFF",

                  "Brown" to "#A52A2A",
                  "Cyan" to "#00FFFF",
                  "Magenta" to "#FF00FF",
                  "Lime" to "#00FF00",
                  "Maroon" to "#800000",
                  "Navy" to "#000080",
                  "Olive" to "#808000",
                  "Teal" to "#008080",
                  "Silver" to "#C0C0C0",
                  "Gray" to "#808080",
                  "Gold" to "#FFD700",

                  "Aqua" to "#00FFFF",
                  "Fuchsia" to "#FF00FF",
                  "Lavender" to "#E6E6FA",
                  "Lawn Green" to "#7CFC00",
                  "Lemon Chiffon" to "#FFFACD",
                  "Light Blue" to "#ADD8E6",
                  "Light Coral" to "#F08080",
                  "Light Cyan" to "#E0FFFF",
                  "Light Gray" to "#D3D3D3",
                  "Light Green" to "#90EE90",
                  "Light Pink" to "#FFB6C1",
                  "Light Salmon" to "#FFA07A",
                  "Light Sea Green" to "#20B2AA",
                  "Light Sky Blue" to "#87CEFA",
                  "Light Slate Gray" to "#778899",
                  "Light Steel Blue" to "#B0C4DE",
                  "Light Yellow" to "#FFFFE0",
                  "Lilac" to "#CDB5CD",

                  "Mint Cream" to "#F5FFFA",
                  "Misty Rose" to "#FFE4E1",
                  "Moccasin" to "#FFE4B5",
                  "Navajo White" to "#FFDEAD",
                  "Old Lace" to "#FDF5E6",
                  "Olive Drab" to "#6B8E23",
                  "Orange Red" to "#FF4500",
                  "Orchid" to "#DA70D6",
                  "Pale Goldenrod" to "#EEE8AA",
                  "Pale Green" to "#98FB98",
                  "Pale Turquoise" to "#AFEEEE",
                  "Pale Violet Red" to "#DB7093",
                  "Papaya Whip" to "#FFEFD5",
                  "Peach Puff" to "#FFDAB9",
                  "Peru" to "#CD853F",
                  "Plum" to "#DDA0DD",
                  "Powder Blue" to "#B0E0E6",
                  "Rosy Brown" to "#BC8F8F",
                  "Royal Blue" to "#4169E1",
                  "Saddle Brown" to "#8B4513",

                  "Tan" to "#D2B48C",
                  "Thistle" to "#D8BFD8",
                  "Tomato" to "#FF6347",
                  "Turquoise" to "#40E0D0",
                  "Violet" to "#EE82EE",
                  "Wheat" to "#F5DEB3",
                  "White Smoke" to "#F5F5F5",
                  "Yellow Green" to "#9ACD32",


                  "Alice Blue" to "#F0F8FF",
                  "Antique White" to "#FAEBD7",
                  "Aqua" to "#00FFFF",
                  "Aquamarine" to "#7FFFD4",
                  "Azure" to "#F0FFFF",
                  "Beige" to "#F5F5DC",
                  "Bisque" to "#FFE4C4",
                  "Blanched Almond" to "#FFEBCD",
                  "Blue" to "#0000FF",
                  "Blue Violet" to "#8A2BE2",
                  "Brown" to "#A52A2A",
                  "Burly Wood" to "#DEB887",
                  "Cadet Blue" to "#5F9EA0",
                  "Chartreuse" to "#7FFF00",
                  "Chocolate" to "#D2691E",
                  "Coral" to "#FF7F50",
                  "Cornflower Blue" to "#6495ED",
                  "Cornsilk" to "#FFF8DC",
                  "Crimson" to "#DC143C",
                  "Cyan" to "#00FFFF",
                  "Dark Blue" to "#00008B",

                  "Dark Cyan" to "#008B8B",
                  "Dark Goldenrod" to "#B8860B",
                  "Dark Gray" to "#A9A9A9",
                  "Dark Green" to "#006400",
                  "Dark Khaki" to "#BDB76B",
                  "Dark Magenta" to "#8B008B",
                  "Dark Olive Green" to "#556B2F",
                  "Dark Orange" to "#FF8C00",
                  "Dark Orchid" to "#9932CC",
                  "Dark Red" to "#8B0000",
                  "Dark Salmon" to "#E9967A",
                  "Dark Sea Green" to "#8FBC8F",
                  "Dark Slate Blue" to "#483D8B",
                  "Dark Slate Gray" to "#2F4F4F",
                  "Dark Turquoise" to "#00CED1",
                  "Dark Violet" to "#9400D3",
                  "Deep Pink" to "#FF1493",
                  "Deep Sky Blue" to "#00BFFF",
                  "Dim Gray" to "#696969",
                  "Dodger Blue" to "#1E90FF",
                  "Firebrick" to "#B22222",
                  "Floral White" to "#FFFAF0",
                  "Forest Green" to "#228B22",
                  "Fuchsia" to "#FF00FF",
                  "Gainsboro" to "#DCDCDC",
                  "Ghost White" to "#F8F8FF",

                  "Goldenrod" to "#DAA520",
                  "Gray" to "#808080",
                  "Green" to "#008000",
                  "Green Yellow" to "#ADFF2F",
                  "Honeydew" to "#F0FFF0",
                  "Hot Pink" to "#FF69B4",
                  "Indian Red" to "#CD5C5C",
                  "Indigo" to "#4B0082",
                  "Ivory" to "#FFFFF0",
                  "Khaki" to "#F0E68C",
                  "Lavender" to "#E6E6FA",
                  "Lavender Blush" to "#FFF0F5",
                  "Lawn Green" to "#7CFC00",
                  "Lemon Chiffon" to "#FFFACD",
                )
                  .filter {
                    val current = this@autoComplete.focusedOption.value
                    val lowercase = it.first.lowercase()
                    if (current.isBlank()) return@filter true
                    for (part in current.lowercase().split(
                      '-',
                      ' ',
                      '.',
                      '_',
                      '/',
                      '\\',
                      '(',
                      ')',
                      '[',
                      ']',
                      '{',
                      '}',
                      ':',
                      ';',
                      '"',
                      '\'',
                      '`',
                      '|',
                      '~',
                      '!',
                      '@',
                      '#',
                      '$',
                      '%',
                      '^',
                      '&',
                      '*',
                      ' ',
                      '\t',
                      '\n',
                      '\r'
                    )) {
                      if (part.isBlank()) continue
                      if (part in lowercase) {
                        return@filter true
                      }
                    }

                    return@filter false
                  }
                  .toMutableList()
                  .take(25)
                  .forEach {
                    choice(it.first, it.second)
                  }
              }
            }

            "description" -> {
              this@autoComplete.suggestString { }
            }

            else -> {
              this@autoComplete.suggestString { }
            }
          }
        }
      }
    }
  }

  inner class ConfigureArgs : Arguments() {
    val type by stringChoice {
      name = "type"
      description = "Whether to configure a channel or role."

      choice("Channel", "channel")
      choice("Role", "role")
    }

    val key by string {
      name = "key"
      description = "The channel or role to configure."

      // Dynamically generate the choices
      this@string.autoComplete {
        val channel1 = this.getChannel().fetchChannelOrNull() ?: return@autoComplete

        if (channel1 !is TextChannelBehavior) return@autoComplete
        val guildId = channel1.guildId
        val guild = channel1.guild

        println("Auto complete: " + this.command.strings)
        this.command.strings["type"]?.let { type ->
          when (type) {
            "channel" -> {
              this.run {
                val current = this.focusedOption.value

                // Loop though channels in server
                guild.channels
                  .filterIsInstance<TextChannel>()
                  .filter {
                    val lowercase = current.lowercase()
                    for (part in it.name.lowercase().split('-', ' ', '.')) {
                      if (lowercase in part) {
                        return@filter true
                      }
                    }

                    return@filter false
                  }
                  .toList()
                  .sortedBy { it.rawPosition }
                  .map {
                    Pair(
                      it.name,
                      it.id.toString()
                    )
                  }
                  .take(25)
                  .toMutableList()
                  .also {
                    it.addFirst("-- Server --" to guild.id.toString())
                  }
                  .also {
                    this@autoComplete.suggestString {
                      for (entry in it) {
                        this@suggestString.choice(entry.first, entry.second)
                      }
                    }
                  }
              }
            }

            "role" -> {
              this.run {
                val current = this.focusedOption.value

                // Loop though roles in server
                guild.roles
                  .filter { it.name != "@everyone" }
                  .filter {
                    val lowercase = current.lowercase()
                    for (part in it.name.lowercase()
                      .split('-', ' ', '.', ':', ';', ',', '!', '?')) {
                      if (lowercase in part) {
                        return@filter true
                      }
                    }
                    return@filter false
                  }
                  .toList()
                  .sortedBy { it.rawPosition }
                  .take(25)
                  .map { Pair(it.name, it.id.toString()) }
                  .also {
                    this@autoComplete.suggestString {
                      for (entry in it) {
                        this@suggestString.choice(entry.first, entry.second)
                      }
                    }
                  }
              }
            }

            else -> {
              this.run {
                this@autoComplete.suggestStringMap(
                  mutableMapOf("None" to "None")
                )
              }
            }
          }
        }
      }
    }

    val property by string {
      name = "property"
      description = "The property to configure."

      autoComplete {
        run {
          this.command.strings["type"]?.let { type ->
            when (type) {
              "channel" -> {
                this@autoComplete.suggestString {
                  choice("Name", "name")
                  choice("Topic", "topic")
                  choice("Type", "type")
                  choice("Category", "category")
                  choice("Color", "color")
                }
              }

              "role" -> {
                this@autoComplete.suggestString {
                  choice("Name", "name")
                  choice("Type", "type")
                  choice("Hoisted", "hoisted")
                  choice("Mentionable", "mentionable")
                  choice("Color", "color")
                }
              }

              else -> {
                this@autoComplete.suggestStringMap(
                  mutableMapOf("None" to "None")
                )
              }
            }
          }
        }
      }
    }
    val value by string {
      name = "value"
      description = "The value of the property."

      validate {
        context.getUser()?.let {
          if (context.getGuild()
              ?.let { it1 -> it.asMember(it1.id).hasPermission(Permission.Administrator) } == true
          ) {
            pass()
            return@let
          }

          fail("You must be an administrator to use this command.")
        } ?: fail("You must be in a guild to use this command.")
      }

      autoComplete {
        val focusedChannel = getChannel().fetchChannelOrNull() ?: return@autoComplete
        if (focusedChannel !is TextChannelBehavior) return@autoComplete
        val guild = focusedChannel.guild

        when (command.strings["type"]) {
          "channel" -> {
            when (command.strings["property"]) {
              "type" -> suggestString {
                run {
                  ChannelUsageType.entries.map { choice(it.displayName, it.name) }
                }
              }

              "category" -> suggestString {
                run {
                  guild.channels.filter {
                    it.type == ChannelType.GuildCategory
                  }.map {
                    choice(it.name, it.id.toString())
                  }
                }
              }

              else -> suggest(listOf())
            }
          }

          "role" -> {
            when (command.strings["property"]) {
              "type" -> suggestString {
                run {
                  RoleUsageType.entries.map { choice(it.displayName, it.name) }
                }
              }

              "color" -> suggestString {
                this@suggestString.minLength = 4
                this@suggestString.maxLength = 7
              }

              else -> suggest(listOf())
            }
          }
        }
      }
    }
  }
}

private fun String.stringToColor(): Int {
  return if (this.startsWith("#")) {
    if (this.length <= 3) {
      val r = if (this.isNotEmpty()) Integer.parseInt(this.substring(1, 2), 16) else 0
      val g = if (this.length >= 2) Integer.parseInt(this.substring(2, 3), 16) else 0
      val b = if (this.length >= 3) Integer.parseInt(this.substring(3, 4), 16) else 0
      return Color(r, g, b).rgb
    }
    Integer.parseInt(this.substring(1, this.length), 16)
  } else {
    throw IllegalArgumentException("Invalid color: $this, must start with #")
  }
}
