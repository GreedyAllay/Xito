package io.github.ultreon.bot

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.storage.toml.TomlDataAdapter
import com.kotlindiscord.kord.extensions.types.FailureReason
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.Config
import io.github.ultreon.bot.ext.*
import io.github.ultreon.bot.utils.readFile
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock.System.now
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.EnvironmentAccess
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.io.IOAccess
import java.time.Instant

val start = now()

lateinit var kord: Kord
  private set

@Deprecated(level = DeprecationLevel.ERROR, message = "Use bot.kordRef instead")
lateinit var botGuildId: Snowflake
  private set

internal val graalContext
  get() = Context.newBuilder("js", "python")
    .allowIO(IOAccess.NONE)
    .allowHostAccess(HostAccess.NONE)
    .allowCreateThread(false)
    .allowCreateProcess(false)
    .allowEnvironmentAccess(EnvironmentAccess.NONE)
    .allowHostClassLoading(false)
    .allowHostClassLookup { false }
    .allowPolyglotAccess(PolyglotAccess.NONE)
    .allowNativeAccess(false)
    .allowAllAccess(false)
    .allowInnerContextOptions(false)
    .allowExperimentalOptions(false)
    .allowValueSharing(false)
    .build()

@OptIn(PrivilegedIntent::class)
class Launch {
  suspend fun setup0() {
    val config = gson.fromJson(readFile("config.json"), Config::class.java)

    while (true) {
      val bot = ExtensibleBot(config.token) {
        presence {
          status = PresenceStatus.Online
          state = "Looking over the server"
          since = start
        }

        intents {
          +Intent.GuildMessages
          +Intent.GuildEmojis
          +Intent.GuildModeration
          +Intent.GuildMessageReactions
          +Intent.GuildVoiceStates
          +Intent.MessageContent
          +Intent.Guilds
          this.build()
        }

        errorResponse { message, type ->
          this.embed {
            title = "Error"
            description = message
            color = dev.kord.common.Color(0xFF0000)
          }

          if (type !is FailureReason.ExecutionError) {
            return@errorResponse
          }

          flags = MessageFlags()

          val stackTrace = type.error.stackTraceToString().encodeToByteArray()
          this.addFile("stacktrace.txt", ChannelProvider(stackTrace.size.toLong()) {
            ByteReadChannel(stackTrace)
          })
        }

        dataAdapter {
          return@dataAdapter TomlDataAdapter()
        }

        extensions {
          add(::MainExtension)
          add(::WelcomeExtension)
          add(::CountingGameExtension)
          add(::ConfigurationExtension)
          add(::ModerationExtension)
          add(::QuarantineExtension)
          add(::LevelsExtension)
        }
      }

      kord = bot.kordRef

      bot.startAsync().join()
    }
  }

  fun setup() = runBlocking {
    setup0()
  }
}

val gson = GsonBuilder().setPrettyPrinting().registerTypeAdapter(Instant::class.java, InstantAdapter).create()

object InstantAdapter : TypeAdapter<Instant>() {
  override fun write(out: JsonWriter, value: Instant) {
    out.value(value.toEpochMilli())
  }

  override fun read(`in`: JsonReader): Instant {
    return Instant.ofEpochMilli(`in`.nextLong())
  }
}
