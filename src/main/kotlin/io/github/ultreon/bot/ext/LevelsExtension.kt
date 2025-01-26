package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.decimal
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMember
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.Permission
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.ServerDataManager
import io.github.ultreon.bot.data.UserDataManager
import io.github.ultreon.bot.data.calcRequiredLevelUp
import io.github.ultreon.bot.utils.snowflake
import kotlinx.datetime.Clock
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class LevelsExtension : Extension() {
  override val name: String = "levels"

  private val userData = UserDataManager

  override suspend fun setup() {
    event<MessageCreateEvent> {
      action {
        // Add XP to the user.
        val memberId = event.member?.id ?: return@action
        println("MemberId: $memberId")

        val message = event.message

        val guildId = event.guildId ?: return@action
        val data = userData[guildId, memberId]
        if (data.lastMessageTime.plus(1.minutes) > Clock.System.now()) return@action

        val serverData = ServerDataManager[guildId]
        if (serverData.levelBlacklist.contains(message.channelId)) return@action

        data.lastMessageTime = Clock.System.now()

        data.addXp(Random.nextDouble(15.0, 25.0) + message.content.length / 20.0, message)
        data.save()

        println("${data.level} ${data.xp} ${data.lastMessageTime} - memberId: $memberId, guildId: $guildId")
        serverData.save()

        data.save()
      }
    }

    publicSlashCommand {
      name = "levels"
      description = "Leveling related commands."

      publicSubCommand(::MemberArgs) {
        name = "view"
        description = "View the level of a user."

        action {
          val m = arguments.member ?: member ?: run {
            respond { content = "Error 404: Member not found." }
            return@action
          }

          val data = userData[m.guildId, m.id]
          data.save()
          val fetchedGuild = m.guild.fetchGuildOrNull()
          if (m == this.member) {
            respond {
              embed {
                title = "Your level in ${fetchedGuild?.name ?: "this server"}"
                field {
                  name = "Level"
                  value = "${data.level}"
                  inline = true
                }

                field {
                  name = "XP"
                  value = "${data.xp.roundToLong()}"
                  inline = true
                }

                field {
                  name = "Required XP to level up"
                  value = "${calcRequiredLevelUp(data.level)}"
                  inline = true
                }

                addInfo(this, this@action)
              }
            }

            return@action
          }

          val fetchedMember = m.fetchMemberOrNull()
          respond {
            embed {
              title =
                "Level of ${fetchedMember?.effectiveName ?: "this user"} in ${fetchedGuild?.name ?: "this server"}"
              field {
                name = "Level"
                value = "${data.level}"
                inline = true
              }

              field {
                name = "XP"
                value = "${data.xp.roundToLong()}"
                inline = true
              }

              field {
                name = "Required XP to level up"
                value = "${calcRequiredLevelUp(data.level)}"
                inline = true
              }

              addInfo(this, this@action)
            }
          }
        }
      }

      publicSubCommand(::CheckLevelRequirements) {
        name = "calc-required-xp"
        description = "View how much xp is needed to level up at a specific level."

        action {
          val level = arguments.level
          respond {
            embed {
              title = "XP needed to level up at level $level"
              field {
                name = "XP Needed"
                value = "${calcRequiredLevelUp(level)}"
              }
            }
          }
        }
      }

      publicSubCommand(::AddXpArgs) {
        name = "add-xp"
        description = "Add XP to a user."

        check { hasPermission(Permission.ManageGuild) }

        action {
          val m = arguments.member ?: member ?: run {
            respond { content = "Error 404: Member not found." }
            return@action
          }

          val data = userData[m.guildId, m.id]
          data.save()
          if (m == this.member) {
            data.addXp(arguments.amount, null)
            respond { content = "Added ${arguments.amount} XP to your level." }
            return@action
          }

          data.addXp(arguments.amount, null)
          val fetchMemberOrNull = m.fetchMemberOrNull()
          respond { content = "Added ${arguments.amount} XP to ${fetchMemberOrNull?.effectiveName}'s level." }
        }
      }

      publicSubCommand(::SetXpLevelArgs) {
        name = "set"
        description = "Set the Level and XP of a user."

        check { hasPermission(Permission.ManageGuild) }

        action {
          val m = arguments.member ?: member ?: run {
            respond { content = "Error 404: Member not found." }
            return@action
          }

          val data = userData[m.guildId, m.id]
          data.level = arguments.level
          data.xp = arguments.xp
          data.save()

          val fetchMemberOrNull = m.fetchMemberOrNull()
          respond {
            content = "Set ${fetchMemberOrNull?.effectiveName}'s level to ${arguments.level} and XP to ${arguments.xp}."
          }
        }
      }

      publicSubCommand(::TopArgs) {
        name = "top"
        description = "View the top level users."

        action {
          val serverData = ServerDataManager[guild?.id ?: return@action]
          val topRankings = serverData.topRankings
          if (topRankings == null) {
            respond { content = "No top rankings found." }
            return@action
          }
          respond {
            embed {
              title = "Top level users"

              field {
                name = "Rank"
                value = topRankings.entries.sortedWith { e1, e2 ->
                  when {
                    e1.value.first == e2.value.first && e1.value.second == e2.value.second -> 0
                    e1.value.first != e2.value.first -> e2.value.first.compareTo(e1.value.first)
                    e1.value.second != e2.value.second -> e2.value.second.compareTo(e1.value.second)
                    else -> 0
                  }
                }.take(arguments.amount).mapIndexed { i, e ->
                  val key = guild?.getMemberOrNull(e.key.toULongOrNull()?.snowflake ?: 0uL.snowflake)?.mention
                    ?: bot.kordRef.getUser(e.key.toULongOrNull()?.snowflake ?: 0uL.snowflake)?.effectiveName
                    ?: e.key.toString()
                  "${i + 1}. $key - lvl. ${e.value.first}, xp. ${e.value.second.roundToLong()}"
                }.joinToString("\n")
              }
            }
          }
        }
      }
    }
  }

  inner class TopArgs : Arguments() {
    val amount by defaultingInt {
      name = "amount"
      description = "The amount of users to show."

      minValue = 10
      maxValue = 100
      defaultValue = 10
    }
  }

  inner class SetXpLevelArgs : Arguments() {
    val level by int {
      name = "level"
      description = "The level to set."

      minValue = 1
      maxValue = 9999
    }

    val xp by decimal {
      name = "xp"
      description = "The amount of XP to set."
    }

    val member by optionalMember {
      name = "member"
      description = "The member to set the level of."
    }
  }

  inner class AddXpArgs : Arguments() {
    val amount by decimal {
      name = "amount"
      description = "The amount of XP to add."
    }

    val member by optionalMember {
      name = "member"
      description = "The member to add XP to."
    }
  }

  inner class CheckLevelRequirements : Arguments() {
    val level by int {
      name = "level"
      description = "The level to check."

      minValue = 1
      maxValue = 9999
    }
  }

  inner class MemberArgs : Arguments() {
    val member by optionalMember {
      name = "member"
      description = "The member to get the level of."
    }
  }
}