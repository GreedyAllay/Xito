package io.github.ultreon.bot.ext

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.common.Color
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.embed
import io.github.ultreon.bot.data.ServerDataManager
import io.github.ultreon.bot.utils.ChannelUsageType
import io.github.ultreon.bot.utils.snowflake

class WelcomeExtension : Extension() {
  override val name: String = "welcome"
  override suspend fun setup() {
    this.event<MemberJoinEvent> {
      action {
        val serverData = ServerDataManager[event.guildId]
        serverData.channelTypes[ChannelUsageType.WELCOME]?.let { ch ->
          kord.getChannel(ch.snowflake)?.let { channel ->
            when (channel) {
              is TextChannelBehavior -> {
                channel.createMessage {
                  embed {
                    title =
                      "Welcome to ${channel.guild.asGuild().name}, ${event.member.effectiveName}!"
                    description = "${event.member.mention} we hope you enjoy your stay!".let {
                      var msg = it

                      if (ChannelUsageType.RULES in serverData.channelTypes)
                        msg += "\nCheck out the rules in <#${serverData.channelTypes[ChannelUsageType.RULES]?.snowflake}>"
                      if (ChannelUsageType.INFO in serverData.channelTypes)
                        msg += "\nCheck out the team information in <#${serverData.channelTypes[ChannelUsageType.INFO]?.snowflake}>"

                      msg
                    }

                    color = Color(0x00bb00)

                    event.member.avatar.let { avatar -> avatar ?: event.member.defaultAvatar }.let {
                      thumbnail {
                        val format = if (it.isAnimated) Image.Format.GIF else Image.Format.PNG

                        url = it.cdnUrl.toUrl {
                          this.format = format
                          this.size = Image.Size.Size1024
                        }
                      }
                    }

                    timestamp = event.member.joinedAt

                    author {
                      name = "Member Joined"
                      icon = event.guild.asGuild().icon?.cdnUrl?.toUrl() ?: ""
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}