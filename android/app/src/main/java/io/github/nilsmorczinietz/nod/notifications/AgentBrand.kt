package io.github.nilsmorczinietz.nod.notifications

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import io.github.nilsmorczinietz.nod.R

/**
 * The icon and accent colour a notification wears: the agent that asks
 * ("Claude Code" shows the Claude mark), or the Nod mark for scripts and
 * agents we have no icon for. Icons come from android/scripts/generate_agent_icons.py.
 */
enum class AgentBrand(@param:DrawableRes val icon: Int, @param:ColorRes val color: Int, private vararg val names: String) {
    Claude(R.drawable.ic_stat_agent_claude, R.color.agent_claude, "claude"),
    Cursor(R.drawable.ic_stat_agent_cursor, R.color.agent_mono, "cursor"),
    Gemini(R.drawable.ic_stat_agent_gemini, R.color.agent_gemini, "gemini"),
    Windsurf(R.drawable.ic_stat_agent_windsurf, R.color.agent_mono, "windsurf", "codeium"),
    // VS Code's MCP client is Copilot; the CLI names it after the first word ("Visual").
    Copilot(R.drawable.ic_stat_agent_copilot, R.color.agent_mono, "copilot", "vscode", "visual studio code", "visual"),
    Cline(R.drawable.ic_stat_agent_cline, R.color.agent_mono, "cline"),
    Zed(R.drawable.ic_stat_agent_zed, R.color.agent_zed, "zed"),
    OpenCode(R.drawable.ic_stat_agent_opencode, R.color.agent_mono, "opencode"),
    Warp(R.drawable.ic_stat_agent_warp, R.color.agent_warp, "warp"),
    Mistral(R.drawable.ic_stat_agent_mistral, R.color.agent_mistral, "mistral"),
    Nod(R.drawable.ic_stat_nod, R.color.nod_primary);

    companion object {
        /** Matches whole words, so "Claude Code", "claude-ai" and "Gemini CLI" find their brand. */
        fun of(agent: String): AgentBrand {
            val words = agent.lowercase().split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty)
            val phrase = words.joinToString(" ")
            return entries.firstOrNull { brand ->
                brand.names.any { name -> if (' ' in name) phrase.contains(name) else name in words }
            } ?: Nod
        }
    }
}
