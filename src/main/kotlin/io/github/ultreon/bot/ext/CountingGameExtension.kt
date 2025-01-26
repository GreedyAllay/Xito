package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.any
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.GuildChannelBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.threads.ThreadChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.rest.builder.message.allowedMentions
import io.github.ultreon.bot.data.ServerData
import io.github.ultreon.bot.data.ServerDataManager
import io.github.ultreon.bot.data.UserDataManager
import io.github.ultreon.bot.graalContext
import io.github.ultreon.bot.utils.ChannelUsageType
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.datetime.Clock
import org.graalvm.polyglot.Value
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.days

private const val stdlib = """
                function abs(x) {
                    if (x < 0) {
                        return -x
                    }
                    return x
                }
                
                function max(x, y) {
                    if (x > y) {
                        return x
                    }
                    return y
                }
                
                function min(x, y) {
                    if (x < y) {
                        return x
                    }
                    return y
                }
                
                function pow(x, y) {
                    return Math.pow(x, y)
                }
                
                function sqrt(x) {
                    return Math.sqrt(x)
                }
                
                function floor(x) {
                    return Math.floor(x)
                }
                
                function ceil(x) {
                    return Math.ceil(x)
                }
                
                function round(x) {
                    return Math.round(x)
                }
                
                function sin(x) {
                    return Math.sin(x)
                }
                
                function cos(x) {
                    return Math.cos(x)
                }
                
                function tan(x) {
                    return Math.tan(x)
                }
                
                function asin(x) {
                    return Math.asin(x)
                }
                
                function acos(x) {
                    return Math.acos(x)
                }
                
                function atan(x) {
                    return Math.atan(x)
                }
                
                function atan2(x, y) {
                    return Math.atan2(x, y)
                }
                
                function log(x) {
                    return Math.log(x)
                }
                
                function log10(x) {
                    return Math.log10(x)
                }
                
                function exp(x) {
                    return Math.exp(x)
                }
                
                function mod(x, y) {
                    return x % y
                }
                
                function random() {
                    return Math.random()
                }
                
                function trunc(x) {
                    return Math.trunc(x)
                }
                
                function sign(x) {
                    if (x > 0) {
                        return 1
                    }
                    if (x < 0) {
                        return -1
                    }
                    return 0
                }
                
                function pow10(x) {
                    return Math.pow(10, x)
                }
                
                function pow2(x) {
                    return Math.pow(2, x)
                }
                
                function pow3(x) {
                    return Math.pow(3, x)
                }
                
                function root(x, y) {
                    return Math.pow(x, 1 / y)
                }
                
                function hypot(x, y) {
                    return Math.hypot(x, y)
                }
            """

class CountingGameExtension : Extension() {
  override val name: String = "counting-game"

  override suspend fun setup() {
    this.event<MessageDeleteEvent> { action { onMessageDelete() } }
    this.event<MessageUpdateEvent> { action { onMessageUpdate() } }
    this.event<MessageCreateEvent> { action { onMessageCreate() } }
    this.event<ReactionAddEvent> { action { onReactionAdd() } }

    this.publicSlashCommand {
      name = "counting"
      description = "Start related commands"

      this.ephemeralSubCommand(::DefineVarArgs) {
        name = "define"
        description = "Define a variable"

        action {
          val name = arguments.name
          val value = arguments.value

          if (name.length != 1) {
            respond {
              content = "Variable name must be a single character"
            }
            return@action
          }

          val channel = event.interaction.channel
          if (channel !is GuildChannelBehavior) {
            respond {
              content = "This command can only be used in a discord guild"
            }
            return@action
          }
          val member = this.member?.fetchMemberOrNull() ?: return@action
          val num = parseNumber(value, member)?.let {
            UserDataManager[channel.guild.id, member.id].variables!![name] = it
          } ?: run {
            respond {
              content = "Unable to calculate the result of the given value!"
            }
            return@action
          }

          respond {
            content = "Variable $name set to $num"
          }
        }
      }
      this.ephemeralSubCommand(::UndefineVarArgs) {
        name = "undefine"
        description = "Remove a variable"

        action {
          val name = arguments.name

          if (name.length != 1) {
            respond {
              content = "Variable name must be a single character"
            }
            return@action
          }

          val channel = event.interaction.channel
          if (channel !is GuildChannelBehavior) {
            respond {
              content = "This command can only be used in a discord guild"
            }
            return@action
          }
          val member = this.member?.fetchMemberOrNull() ?: return@action
          UserDataManager[channel.guild.id, member.id].variables!!.remove(name)

          respond {
            content = "Variable `$name` removed"
          }
        }
      }

    }
  }

  inner class DefineVarArgs : Arguments() {
    val name by string {
      name = "name"
      description = "Name of the variable"
    }

    val value by string {
      name = "value"
      description = "Value of the variable"
    }
  }

  inner class UndefineVarArgs : Arguments() {
    val name by string {
      name = "name"
      description = "Name of the variable"
    }
  }

  private suspend fun EventContext<MessageDeleteEvent>.onMessageDelete() {
    val message = event.message ?: return
    val channel = message.channel.fetchChannel()

    if (channel is TextChannel || channel is ThreadChannel) {
      val serverData = when (channel) {
        is TextChannel -> ServerDataManager[channel.guild.id]
        is ThreadChannel -> ServerDataManager[channel.getParent().guild.id]
        else -> throw Exception("Unknown channel type")
      }

      val channelData = serverData.channelTypes[ChannelUsageType.COUNTING_GAME] ?: run {
        return
      }
      if (channel.id.value != channelData) {
        return
      }

      parseNumber(
        message.content,
        message.author?.fetchMemberOrNull(message.getGuildOrNull()?.id ?: return) ?: return,
        message,
      )?.let {
        if (serverData.lastCountMessageId == message.id) {
          val createMessage = channel.createMessage {
            content =
              "${serverData.lastCountNumber}    *(${event.message?.author?.mention} deleted their count of ${serverData.lastCountNumber}!)*"
          }

          serverData.lastCountMessageId = createMessage.id
          createMessage.addReaction(ReactionEmoji.Unicode("✅"))
        }
      }
    }
  }

  private suspend fun EventContext<ReactionAddEvent>.onReactionAdd() {
    val message = event.message
    val channel = message.channel.fetchChannel()

    val serverData = when (channel) {
      is TextChannelBehavior -> ServerDataManager[channel.guildId]
      is ThreadChannelBehavior -> ServerDataManager[channel.getParent().guildId]
      else -> throw Exception("Unknown channel type")
    }
    val channelData = serverData.channelTypes[ChannelUsageType.COUNTING_GAME] ?: run {
      return
    }
    if (channel.id.value != channelData) {
      return
    }

    val array = arrayOf(
      "✅",
      "❌",
      "⭕",
      "☑️",
      "🔥",
      "🤣"
    )

    if (event.userId != kord.selfId) {
      if (event.emoji is ReactionEmoji.Unicode) {
        if (event.emoji.name in array) {
          if (event.message.getReactors(event.emoji).any { it.id == kord.selfId }) {
            event.message.addReaction(ReactionEmoji.Unicode("🐛")) // Bug emoji
          }
          event.message.deleteReaction(event.emoji)
        }
      }
    }
  }

  private suspend fun EventContext<MessageUpdateEvent>.onMessageUpdate() {
    val message = event.message.asMessage()
    val channel = message.channel.fetchChannel()

    val serverData = when (channel) {
      is TextChannelBehavior -> ServerDataManager[channel.guildId]
      is ThreadChannelBehavior -> ServerDataManager[channel.getParent().guildId]
      else -> throw Exception("Unknown channel type")
    }
    val channelData = serverData.channelTypes[ChannelUsageType.COUNTING_GAME] ?: run {
      return
    }
    if (channel.id.value != channelData) {
      return
    }

    parseNumber(
      message.content,
      message.author?.fetchMemberOrNull(message.getGuildOrNull()?.id ?: return) ?: return,
      message
    )?.let {
      if (serverData.lastCountMessageId == message.id) {
        val createMessage = channel.createMessage {
          content =
            "${serverData.lastCountNumber}    *(${message.author?.mention} edited their count of ${serverData.lastCountNumber}!)*"
        }

        serverData.lastCountMessageId = createMessage.id
        createMessage.addReaction(ReactionEmoji.Unicode("✅"))
      }
    }
  }

  private suspend fun EventContext<MessageCreateEvent>.onMessageCreate() {
    val message = event.message
    val channel = message.channel.fetchChannel()

    val serverData = when (channel) {
      is TextChannelBehavior -> ServerDataManager[channel.guildId]
      is ThreadChannelBehavior -> ServerDataManager[channel.getParent().guildId]
      else -> throw Exception("Unknown channel type")
    }
    val channelData = serverData.channelTypes[ChannelUsageType.COUNTING_GAME] ?: run {
      return
    }
    if (channel.id.value != channelData) {
      return
    }

    val author = message.author
    if (author == null || author.isBot) {
      if (author?.id != kord.selfId)
        message.addReaction(ReactionEmoji.Unicode("🤖"))
      return
    }

    val content = message.content
    val count =
      parseNumber(content, author.fetchMemberOrNull(message.getGuildOrNull()?.id ?: return) ?: return, message)
        ?: return

    if (checkDoubleCount(author, message, channel, serverData)) return
    if (checkFunnyNumbers(count, message)) return

    serverData.lastCountedMember = author.id

    val lastCountNumber = serverData.lastCountNumber

    if (count == lastCountNumber + BigInteger.ONE) {
      serverData.lastCountNumber = count
      serverData.lastCountMessageId = message.id

      if (count > serverData.highestCountNumber) {
        serverData.highestCountNumber = count

        checkImpossibleWin(count, message, channel, author)
        message.addReaction(ReactionEmoji.Unicode("☑️"))
      } else {
        handleValid(count, message)
      }

      serverData.save()
    } else {
      handleFail(message, channel, lastCountNumber, count, author, serverData)
    }

    return
  }

  private suspend fun parseNumber(content: String, member: Member, message: Message? = null): BigInteger? {
    var value = content
    if (value.startsWith("# ") || value.startsWith("## ") || value.startsWith("### ")) {
      value = value.trimStart('#').substring(1)
    }

    value.trim()

    value = value.replace(Regex("\\s+//.*$", RegexOption.IGNORE_CASE), "")

    val bigInteger = value.toBigIntegerOrNull()
    if (bigInteger != null) return bigInteger

    // Scientific calculations
    try {
      if (value.contains("function ")) return null
      if (value.contains(Regex("[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*|[\"'`\\[\\]{}<>]|\\+\\+|--|\\|\\||function |return |class |new |instanceof|getClass|getPrototypeOf|setPrototypeOf|isExtensible|preventExtensions|defineProperty|hasOwnProperty|propertyIsEnumerable|getOwnPropertyDescriptor|getOwnPropertyNames"))) return null
      UserDataManager[member.guildId, member.id].variables!!.entries.map {
        ("(\\s+|^)" + it.key + "(\\s+|$)") to it.value
      }.forEach {
        value = value.replace(Regex(it.first), " ${it.second} ")
      }
      value = value.replace("²√", "sqrt")
      value = value.replace("√", "root")

      graalContext.use { context ->
        // Default math functions
        context.eval("js", stdlib.trimIndent())

        var result: Value? = null

        val t = thread {
          context.enter()
          try {
            result = context.eval("js", value)
          } finally {
            context.leave()
          }
        }
        t.join(1000)

        if (t.isAlive) {
          context.close(true)
          message!!.addReaction(ReactionEmoji.Unicode("⏱️"))
        }

        t.join(1000)
        if (t.isAlive) {
          println("[WATCHDOG] Thread is looping! Safeguard is shutting down the bot!")
          Runtime.getRuntime().halt(-1)
        }

        if (result == null) return null
        if (result!!.isHostObject || result!!.isMetaObject || result!!.isProxyObject || result!!.isNativePointer) {
          // Timeout the user for 3 days, put a warning in the logs and delete the count.
          member.edit {
            timeoutUntil = Clock.System.now() + 3.days
            reason = "Illegal access to bot server!"
          }

          return null
        }
        return if (result!!.isNumber) result!!.`as`(Number::class.java).let {
          when (it) {
            is BigDecimal -> {
              return it.toBigInteger()
            }

            is BigInteger -> {
              return it
            }

            is Double -> {
              return it.toBigDecimal().toBigInteger()
            }

            is Float -> {
              return it.toBigDecimal().toBigInteger()
            }

            is Long -> {
              return it.toBigInteger()
            }

            is Int -> {
              return it.toBigInteger()
            }

            is Short -> {
              return it.toInt().toBigInteger()
            }

            is Byte -> {
              return it.toInt().toBigInteger()
            }

            else -> {
              return it.toDouble().toBigDecimal().toBigInteger()
            }
          }
        } else null
      }
    } catch (t: Throwable) {
      t.printStackTrace()
    }

    return null
  }

  private suspend fun checkFunnyNumbers(count: BigInteger, message: Message): Boolean {
    if (count.toString().length > 100) {
      message.addReaction(ReactionEmoji.Unicode("🤣"))
      return true
    }

    if (count <= BigInteger.ZERO) {
      message.addReaction(ReactionEmoji.Unicode("⭕"))
      return true
    }
    return false
  }

  private suspend fun checkDoubleCount(
    author: User,
    message: Message,
    channel: Channel,
    serverData: ServerData
  ): Boolean {
    if (serverData.lastCountedMember == author.id) {
      message.addReaction(ReactionEmoji.Unicode("❌"))

      serverData.lastCountedMember = null
      serverData.lastCountNumber = 0.toBigInteger()
      serverData.save()

      if (channel is MessageChannelBehavior) {
        val stackTrace = Throwable().stackTraceToString().toByteArray()

        channel.createMessage {
          this.content = "You can't count twice in a row, ${author.mention}!\nNext number is ${1}"

          this.allowedMentions {
            this.users += author.id
          }

          this.addFile("debug_stacktrace.txt", ChannelProvider(stackTrace.size.toLong()) {
            ByteReadChannel(stackTrace)
          })
        }
      }

      return true
    }
    return false
  }

  private suspend fun checkImpossibleWin(
    count: BigInteger,
    message: Message,
    channel: Channel,
    author: User
  ) {
    if (count.toString().length == 100) {
      message.addReaction(ReactionEmoji.Unicode("🔥"))
      if (channel is MessageChannelBehavior) {
        channel.createMessage {
          this.content = """
                               You won the game, ${author.mention}!
                               Counting to a googol was considered impossible, yet you did.
                               """.trimIndent()
        }
      }
    }
  }

  private suspend fun handleValid(count: BigInteger, message: Message) {
    message.addReaction(
      ReactionEmoji.Unicode(
        when {
          count.toString() == "100" -> "💯" // A hundred
          count.toString() == "13" -> "🍀" // Lucky
          count.toString() == "69" -> "👍" // Nice!
          count.toString() == "313" -> "🦆" // Donald duck
          count.toString() == "911" -> "🚓" // Police car
          count.toString() == "666" -> "☢️" // Radiation sign
          count.toString() == "777" -> "🎮" // Game controller
          count.toString() == "1337" -> "🤖" // Robot
          count.toString() == "4000" -> "📸" // Camera with flash
          count.toString() == "10000" -> "❌" // Invalid, or is it?
          count.toString() == "65536" -> "💻" // 16-bit integer
          count.toString() == "69420" -> "🤣" // What?
          count.toString() == "99999" -> "🤯" // Confused
          count.toString() == "2147483647" -> "💥" // What the hashtag?
          else -> "✅" // Valid
        }
      )
    )
  }

  private suspend fun handleFail(
    message: Message,
    channel: Channel,
    lastCountNumber: BigInteger,
    count: BigInteger,
    author: User,
    serverData: ServerData
  ) {
    message.addReaction(ReactionEmoji.Unicode("❌"))
    serverData.lastCountNumber = 0.toBigInteger()
    serverData.lastCountedMember = null
    serverData.save()

    if (channel is MessageChannelBehavior) {
      channel.createMessage {
        this.content =
          "That number should be ${lastCountNumber + BigInteger.ONE}, not $count, ${author.mention}.\n" +
                  "The next number is ${1}"
        this.flags = MessageFlags(MessageFlag.SuppressNotifications)
      }
    }
  }
}