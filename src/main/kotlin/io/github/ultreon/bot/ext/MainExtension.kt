@file:Suppress("t")

package io.github.ultreon.bot.ext

import com.khubla.antlr4example.PythonLexer
import com.khubla.antlr4example.PythonParser
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.member
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMember
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.createdAt
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.Color
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.edit
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.ultreon.interpreter.python3.Python3Interpreter
import dev.ultreon.interpreter.python3.PythonValue
import io.github.ultreon.bot.gson
import io.github.ultreon.bot.start
import io.github.ultreon.bot.utils.MemberArgs
import io.github.ultreon.bot.utils.UserArgs
import io.github.ultreon.bot.utils.createInfoMessage
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import org.antlr.v4.runtime.CommonTokenStream
import java.net.URL
import kotlin.io.path.Path
import kotlin.math.min

class MainExtension : Extension() {

  override val name: String = "main-utils"

  override suspend fun setup() {
    publicSlashCommand {
      name = "ping"
      description = "Pong!"

      action {
        respond {
          embed {
            title = "Pong! :ping_pong:"
            color = Color(0x00ff00)

            field {
              name = "Uptime"
              value = "${(Clock.System.now() - start)}"
            }

            field {
              name = "Gateway Latency"
              value = "${bot.kordRef.gateway.averagePing?.toString()}"
            }

            addInfo(this, this@action)
          }
        }
      }
    }

    publicSlashCommand {
      name = "info"
      description = "Information about the server."

      action {
        respond {
          createInfoMessage(guild)


          this.embeds?.let {
            addInfo(it[0], this@action)
          }
        }
      }
    }

    publicSlashCommand {
      name = "pi"
      description = "Get a random part of the number pi."
      action {
        val offset = (Math.random() * 99_999_999_999_945).toBigDecimal().toBigInteger()
        val url = URL("https://api.pi.delivery/v1/pi?start=${offset}&numberOfDigits=56&radix=10")

        var pi: String = ""
        try {
          pi = gson.fromJson(url.readText(), PiContent::class.java).content
          if (pi.length == 56 && pi.toBigIntegerOrNull() != null) {
            // Check for embed perms
            if (guild == null || appPermissions?.contains(Permission.EmbedLinks) != true) {
              respond {
                content = "# π @ $offset\n```$pi```"
              }
              return@action
            }

            respond {
              embed {
                title = "π @ $offset"
                description = """
                                    ```
                                    $pi
                                    ```""".trimIndent()
                color = Color(3.1415927f.toRawBits())

                addInfo(this, this@action)
              }
            }
          } else {
            respond {
              content = "Error ~~404~~ Pi is now: ```html\n$pi\n```"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
          }
        } catch (e: Exception) {
          if (pi != "") {
            try {
              respond {
                content = "# π @ $offset\n```$pi```"
              }
              return@action
            } catch (e: Exception) {
              e.printStackTrace()

            }
          }
          e.printStackTrace()
          respond {
            content = "Sorry but PI is not available right now :(\nHave a :pie: instead!"
            flags = MessageFlags(MessageFlag.Ephemeral)
          }
        }
      }
    }

    publicSlashCommand {
      name = "user"
      description = "User related commands."

      this.publicSubCommand(::UserArgs) {
        name = "info"
        description = "Information about the user."
        action {
          val user = arguments.user ?: run {
            respond {
              content = "User not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          respond {
            embed {
              title = user.effectiveName
              description = """
                                Information about ${user.mention}
                                -# User ID: `${user.id}`
                            """.trimIndent()
              color = user.accentColor ?: Color(0x000000)
              thumbnail = EmbedBuilder.Thumbnail().let {
                it.url = (user.avatar
                  ?: user.defaultAvatar).let { asset ->
                  val format = if (asset.isAnimated) Image.Format.GIF else Image.Format.PNG
                  asset.cdnUrl.toUrl {
                    this.format = format
                  }
                }
                return@let it
              }

              field {
                name = "Name"
                value = user.globalName ?: "\u200B"
                inline = true
              }

              field {
                name = "Username"
                value = user.username
                inline = true
              }

              field {
                name = "Discriminator"
                value =
                  if (user.discriminator == "0" || user.discriminator == "0000") "N/A" else "#" + user.discriminator
              }

              field {
                name = "Created"
                value = user.fetchUserOrNull()?.createdAt?.toMessageFormat(
                  DiscordTimestampStyle.RelativeTime
                ) ?: "Never"
                inline = true
              }

              field {
                name = "Joined"
                value = user.fetchMemberOrNull(guild!!.id)?.joinedAt?.toMessageFormat(
                  DiscordTimestampStyle.RelativeTime
                ) ?: "Never"
              }

              field {
                name = "Roles"
                value = "```" + user.fetchMember(guild!!.id).roles
                  .toList()
                  .sortedBy { it.rawPosition }.joinToString(separator = "\n") { it.name } + "```"
              }

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand {
        name = "avatar"
        description = "Get the avatar of the user."

        check { hasPermission(Permission.ModerateMembers) }

        action {
          respond {
            embed {
              title = event.interaction.user.effectiveName
              description = "Avatar of ${event.interaction.user.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)
              image = event.interaction.user.avatar?.let {
                val format = if (it.isAnimated) Image.Format.GIF else Image.Format.PNG
                it.cdnUrl.toUrl {
                  this.format = format
                  this.size = Image.Size.Size4096
                }
              }

              addInfo(this, this@action)
            }
          }
        }
      }
    }

    publicSlashCommand {
      name = "roles"
      description = "Role related commands."

      this.publicSubCommand(::MemberArgs) {
        name = "list"
        description = "List the roles of the user."
        action {
          val m = arguments.member ?: member ?: run {
            respond {
              content = "Member not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          val member = m.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          respond {
            embed {
              title = member.effectiveName
              description = "Roles of ${member.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)
              field {
                name = "Roles"
                value = "```" + member.roles
                  .toList()
                  .sortedBy { it.rawPosition }.joinToString(separator = "\n") { it.name } + "```"
              }

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::ModifyRoleArgs) {
        name = "add"
        description = "Add a role to the user."

        check { hasPermission(Permission.ManageRoles) }

        action {
          respond {
            embed {
              title = arguments.member.effectiveName
              description = "Added role ${arguments.role} to ${arguments.member.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::ModifyRoleArgs) {
        name = "remove"
        description = "Remove a role from the user."

        check { hasPermission(Permission.ManageRoles) }

        action {
          respond {
            embed {
              title = arguments.member.effectiveName
              description = "Removed role ${arguments.role} from ${arguments.member.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::ModifyRoleArgs) {
        name = "clear"
        description = "Clear all roles from the user."

        check { hasPermission(Permission.ManageGuild) }

        action {
          respond {
            embed {
              title = arguments.member.effectiveName
              description = "Cleared roles from ${arguments.member.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }
    }

    publicSlashCommand {
      name = "self-nick"
      description = "Self-nick related commands."

      this.publicSubCommand(::SelfNicknameArgs) {
        name = "set"
        description = "Set the nickname of the user."

        action {
          val member = member?.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found."
            }
            return@action
          }

          member.edit {
            this.nickname = arguments.nickname
          }

          respond {
            embed {
              title = event.interaction.user.effectiveName
              description =
                "Set nickname of ${event.interaction.user.mention} to ${arguments.nickname}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::SelfNicknameArgs) {
        name = "remove"
        description = "Remove the nickname of the user."

        action {
          val member = member?.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found."
            }
            return@action
          }

          member.edit {
            this.nickname = null
          }

          respond {
            embed {
              title = event.interaction.user.effectiveName
              description = "Removed your nickname"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }
    }

    publicSlashCommand {
      name = "nick"
      description = "Nickname related commands."


      this.publicSubCommand(::NicknameSetArgs) {
        name = "set"
        description = "Set the nickname of the user."

        check { hasPermission(Permission.ManageNicknames) }

        action {
          val member = arguments.member?.let {
            it.fetchMemberOrNull() ?: run {
              respond {
                content = "Member ${it.id} not found."
              }
              return@action
            }
          } ?: member?.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found."
            }
            return@action
          }

          if (member.id == event.interaction.user.id) {
            respond {
              content = "You can't set your own nickname!"
              flags = MessageFlags(MessageFlag.Ephemeral)
            }
            return@action
          }

          member.edit {
            this.nickname = arguments.nickname
          }

          respond {
            embed {
              title = member.effectiveName
              description =
                "Set nickname of ${member.mention} to ${arguments.nickname}"
              color = member.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::MemberArgs) {
        name = "remove"
        description = "Remove the nickname of the user."

        check { hasPermission(Permission.ManageNicknames) }

        action {
          val member = arguments.member?.let {
            it.fetchMemberOrNull() ?: run {
              respond {
                content = "Member ${it.id} not found."
              }
              return@action
            }
          } ?: member?.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found."
            }
            return@action
          }

          member.edit {
            this.nickname = null
          }

          respond {
            embed {
              title = member.effectiveName
              description = "Removed nickname of ${member.mention}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }

      this.publicSubCommand(::MemberArgs) {
        name = "get"
        description = "Get the nickname of the member."
        action {
          val member = arguments.member?.let {
            it.fetchMemberOrNull() ?: run {
              respond {
                content = "Member ${it.id} not found."
              }
              return@action
            }
          } ?: member?.fetchMemberOrNull() ?: run {
            respond {
              content = "Member not found."
            }
            return@action
          }
          respond {
            embed {
              title = member.effectiveName
              description =
                if (member.nickname == null) "No nickname" else "Nickname of ${member.mention} is ${member.nickname}"
              color = event.interaction.user.accentColor ?: Color(0x0080ff)

              addInfo(this, this@action)
            }
          }
        }
      }
    }

    this.event<MessageCreateEvent> {
      action {
        if (bot.kordRef.selfId != Snowflake(913195632422944799L)) {
          return@action
        }

        if (event.message.content.startsWith("exec: ```")) {
          var jsCode = event.message.content.removePrefix("exec: ```")
          var lang = "none"
          if (jsCode.startsWith("js")) {
            jsCode = jsCode.removePrefix("js")
            lang = "js"
          } else if (jsCode.startsWith("py")) {
            jsCode = jsCode.removePrefix("py")
            lang = "python"
          }
          jsCode = jsCode.removeSuffix("```")
//          try {
//            graalContext.use { context ->
//              // Default math functions
//              var result: Value? = null
//              var error: Throwable? = null
//
//              val t = thread {
//                context.enter()
//                try {
//                  if (lang == "none") {
//                    throw IllegalArgumentException("No language specified.")
//                  } else if (lang == "js") {
//
//                  } else if (lang == "python") {
//                    result = context.eval(lang, """
//try:
//    def init():
//        import sys
//        import traceback
//
//        BLOCKS = [
//            "sys", "os", "io", "types", "typing", "contextlib", "datetime", "collections", "itertools", "operator", "pickle", "tkinter"
//        ]
//
//        # Import allowed libs to be cached
//        class ImportBlocker:
//            def __init__(self, module_name):
//                self.module_name = module_name
//
//            def find_spec(self, fullname, path, target=None):
//                if fullname.split(".")[0] in BLOCKS:
//                    raise ImportError(f"Importing '{self.module_name}' is disabled.")
//
//        # Add the ImportBlocker to sys.meta_path
//        block_urllib = ImportBlocker("urllib")
//        sys.meta_path.insert(0, block_urllib)
//
//        print(sys.meta_path)
//
//        # Overwrite sys.meta_path in next import
//        class ImportOverwrite:
//            def __init__(self):
//                import sys
//                self.sys = sys
//
//                import json, math, random, time, datetime, builtins, pickle, contextlib, collections, itertools, operator
//                self.sys.modules.update({
//                    "json": json,
//                    "math": math,
//                    "random": random,
//                    "time": time,
//                    "datetime": datetime,
//                    "builtins": builtins,
//                    "pickle": pickle,
//                    "contextlib": contextlib,
//                    "collections": collections,
//                    "itertools": itertools,
//                    "operator": operator
//                })
//
//            def find_spec(self, fullname, path, target=None):
//                if fullname == 'sys':
//                    del self.sys.meta_path
//                    return self.sys
//
//                if fullname == 'builtins':
//                    return
//
//        # Add the ImportOverwrite to sys.meta_path
//        block_sys_modification = ImportOverwrite()
//        sys.meta_path.insert(0, block_sys_modification)
//
//        class ReadOnlyMetaPath(list):
//            def __setitem__(self, key, value):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def __delitem__(self, key):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def append(self, item):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def extend(self, iterable):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def insert(self, index, item):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def remove(self, item):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def pop(self, index=-1):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def __getattr__(self, name):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def __setattr__(self, name, value):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//            def __delattr__(self, name):
//                raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//        # Replace sys.meta_path with the read-only version
//        sys.meta_path = ReadOnlyMetaPath(sys.meta_path)
//
//        def block_sys_modification():
//          raise RuntimeError("Modification of sys.meta_path is blocked.")
//
//        # Test: Trying to modify sys.meta_path
//        try:
//            sys.meta_path.append(None)
//            raise Exception("Should not reach here: CODE: 1")
//        except RuntimeError as e:
//            print(e)  # Output: Modification of sys.meta_path is blocked.
//
//        # Test: Trying to modify sys.meta_path
//        try:
//            del sys.meta_path[0]
//            raise Exception("Should not reach here: CODE: 2")
//        except RuntimeError as e:
//            print(e)  # Output: Modification of sys.meta_path is blocked.
//
//        del sys.modules["sys"]
//        del sys
//
//    init()
//except:
//    result = "```" + str.join("", traceback.format_exception(sys.exc_info()[0], sys.exc_info()[1], sys.exc_info()[2])) + "```"
//
//del init
//
//def main():
//${jsCode.prependIndent("  ")}
//
//try:
//    if "result" not in globals() and "result" not in locals():
//        result = main()
//except Exception as e:
//  import traceback
//  result = "```" + str.join("", traceback.format_exception(e.__class__, e, e.__traceback__)) + "```"
//
//result
//                    """)
//                  }
//                } catch (e: Throwable) {
//                  error = e
//                } finally {
//                  context.leave()
//                }
//              }
//              t.join(10000)
//
//              if (t.isAlive) {
//                context.close(true)
//                event.message!!.addReaction(ReactionEmoji.Unicode("⏱️"))
//              }
//
//              t.join(1000)
//              if (t.isAlive) {
//                println("[WATCHDOG] Thread is looping! Safeguard is shutting down the bot!")
//                Runtime.getRuntime().halt(-1)
//              }
//
//              if (error != null) {
//                throw error!!
//              }
//
//              event.message.respond(useReply = true) {
//                embed {
//                  title = "JavaScript Result"
//                  description = result?.let {
//                    if (it.isString) {
//                      it.asString()
//                    } else if (it.isNumber) {
//                      it.asDouble().toString()
//                    } else if (it.isBoolean) {
//                      it.asBoolean().toString()
//                    } else if (it.isNull) {
//                      "null"
//                    } else {
//                      it.toString()
//                    }
//                  } ?: "null"
//                  color = Color(0x0080ff)
//                }
//              }
//            }
//          } catch (e: Throwable) {
//            e.printStackTrace()
//
//            event.message.respond(useReply = true) {
//              embed {
//                title = "Script Error"
//                description = "```\n${e.message ?: "null"}\n```"
//                color = Color(0xff0000)
//              }
//            }
//
//            event.message!!.addReaction(ReactionEmoji.Unicode("❌"))
//        }

          try {

            val code = """
              |def main(args):
              |${jsCode.prependIndent("    ")}
              |
              |result: object = main([])
            """.trimMargin()
              println(code)
            val lexer = PythonLexer(org.antlr.v4.runtime.CharStreams.fromString(code))
            val parser = PythonParser(CommonTokenStream(lexer))
            val tree = parser.file_input();
            Python3Interpreter(Path("main.py"), true).use {
              it.run {
                mainContext["object"] = PythonValue.of(mainContext, stdLib.builtins.objectClass)
                mainContext["tuple"] = PythonValue.of(mainContext, stdLib.builtins.tupleClass)
                mainContext["list"] = PythonValue.of(mainContext, stdLib.builtins.listClass)
                mainContext["dict"] = PythonValue.of(mainContext, stdLib.builtins.dictClass)
              }
              it.visit(tree)
              event.message.respond(useReply = true) {
                embed {
                  title = "Python Result"
                  description = it.mainContext["result"].toString()
                  color = Color(0x0080ff)
                }
              }
            }

          } catch (e: Throwable) {
            val stackTraceToString = e.stackTraceToString()
            if (stackTraceToString.length > 1000) {
              event.message.respond(useReply = true) {
                addFile("debug_stacktrace.txt", ChannelProvider(stackTraceToString.length.toLong()) {
                  ByteReadChannel(stackTraceToString.toByteArray())
                })
                embed {
                  title = "Python Error"
                  description = "See attached file for stack trace"
                  color = Color(0xff0000)
                }
              }

              return@action
            }
            event.message.respond(useReply = true) {
              embed {
                title = "Python Error"
                description = "```\n${
                  stackTraceToString.substring(
                    0..min(
                      1000,
                      e.stackTraceToString().length - 1
                    )
                  ) ?: "null"
                }\n```"
                color = Color(0xff0000)
              }
            }
          }
        }
      }
    }
  }

  data class PiContent(
    val content: String,
  )

  inner class ModifyRoleArgs : Arguments() {
    val member by member {
      name = "user"
      description = "The user to modify."
    }
    val role by role {
      name = "role"
      description = "The role to add."
    }

  }

  inner class NicknameSetArgs : Arguments() {
    val member by optionalMember {
      name = "user"
      description = "The user to modify."
    }
    val nickname by optionalString {
      name = "nickname"
      description = "The nickname to set."
    }

  }

  inner class SelfNicknameArgs : Arguments() {
    val nickname by optionalString {
      name = "nickname"
      description = "The nickname to set."
    }
  }

}

suspend fun <A : Arguments> addInfo(
  embedBuilder: EmbedBuilder,
  publicSlashCommandContext: PublicSlashCommandContext<A, ModalForm>,
) {
  if (publicSlashCommandContext.guild == null) {
    return
  }
  embedBuilder.author {
    name = publicSlashCommandContext.guild!!.fetchGuild().name
    icon = publicSlashCommandContext.guild!!.fetchGuild().icon?.let {
      if (it.isAnimated) {
        it.cdnUrl.toUrl {
          format = Image.Format.GIF
        }
      } else {
        it.cdnUrl.toUrl()
      }
    }
  }

  embedBuilder.footer {
    text = "Requested by ${publicSlashCommandContext.event.interaction.user.effectiveName}"
    icon = publicSlashCommandContext.event.interaction.user.avatar?.let {
      if (it.isAnimated) {
        it.cdnUrl.toUrl {
          format = Image.Format.GIF
        }
      } else {
        it.cdnUrl.toUrl()
      }
    }
  }

  embedBuilder.timestamp = Clock.System.now()
}

