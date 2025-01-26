package io.github.ultreon.bot.utils

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMember
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser

class UserArgs : Arguments() {
  val user by optionalUser {
    name = "user"
    description = "A user."
  }
}

class MemberArgs : Arguments() {
  val member by optionalMember {
    name = "member"
    description = "A member of the server"
  }
}