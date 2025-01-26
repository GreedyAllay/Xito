package io.github.ultreon.bot.utils

enum class ChannelUsageType(val displayName: String) {
  COUNTING_GAME("Counting Game"),

  PROJECT("Project"),
  RULES("Rules"),
  LOGS("Logs"),
  WELCOME("Welcome"),
  INFO("Info"),
  LOCKDOWN("Lockdown")
}

enum class RoleUsageType(val displayName: String) {
  QUARANTINED("Quarantined"),

  MEMBER("Member"),
  SCHEDULED_FOR_BAN("Scheduled For Ban"),
  DEVELOPER("Developer"),
  MODERATOR("Moderator"),
  ADMIN("Admin"),
  OWNER("Owner");

  fun isModerator(): Boolean = this == MODERATOR || this == ADMIN || this == OWNER

  fun isAdministrator(): Boolean = this == ADMIN || this == OWNER

  fun isOwner(): Boolean = this == OWNER
}
