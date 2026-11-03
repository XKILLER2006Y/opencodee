package com.celoext.data

import com.celoext.Graph
import java.io.File

/**
 * Skills that ship inside the app (assets/default-skills) and are used without being asked.
 * They are copied into the skills folder once (a skill the user deletes stays deleted), and a short always-on block
 * in AGENTS.md tells the agent to apply them on its own. The block only mentions skills that are still installed.
 */
object DefaultSkills {
    private const val ASSETS = "default-skills"
    const val SLOP = "anti-ai-slop-writing"
    const val REA = "reverse-engineer-anything"
    private val all = listOf(SLOP, REA)

    /** Copies each default skill that wasn't seeded yet. Needs the Ubuntu rootfs: it is wiped when the environment is installed. */
    fun seed() {
        if (!Graph.paths.rootfsReady) return
        val done = Graph.prefs.seededSkills.split(',').filter { it.isNotBlank() }.toMutableSet()
        var changed = false
        for (name in all) {
            if (name in done) continue
            val target = File(Skills.dir(), name)
            val ok = if (target.exists()) true else try {
                copyAsset("$ASSETS/$name", target)
                true
            } catch (e: Exception) {
                target.deleteRecursively()
                false
            }
            if (ok) { done += name; changed = true }
        }
        if (changed) Graph.prefs.seededSkills = done.joinToString(",")
    }

    private fun copyAsset(path: String, dest: File) {
        val am = Graph.app.assets
        val children = am.list(path)
        if (children.isNullOrEmpty()) {
            dest.parentFile?.mkdirs()
            am.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dest.mkdirs()
            for (c in children) copyAsset("$path/$c", File(dest, c))
        }
    }

    private fun installed(name: String) = File(Skills.dir(), "$name/SKILL.md").isFile

    /** For AGENTS.md. Null when none of the default skills is installed any more. */
    val prompt: String?
        get() {
            val parts = mutableListOf<String>()
            if (installed(SLOP)) parts += """
- $SLOP applies to ALL prose you write for the user, without being asked: chat replies, emails, posts, docs, READMEs, release notes, commit messages, PR text. Apply these short rules silently: lead with the point; be concrete; vary sentence length; use plain verbs; no filler openers (Certainly, Moreover, Great question); no stock phrases (In today's..., It's worth noting, Not just X but Y, I hope this helps); no words like delve, tapestry, landscape, testament, vibrant, pivotal, leverage, seamless, robust; avoid em dashes and exclamation marks; no markdown in text meant to be pasted elsewhere; never invent statistics, quotes or anecdotes. Before writing anything longer than a few sentences, load the skill (skill tool, or read its SKILL.md) and follow it. Do not mention that you use it. Code, command output and the user's own wording are exempt.
""".trim()
            if (installed(REA)) parts += """
- $REA: whenever the user wants to understand how an app, binary, APK, website, Electron or JavaScript app, .NET assembly, firmware or network capture works without its source, load this skill first and follow it, without being asked. Lawful, authorized analysis only. It explains what this phone environment can and cannot do.
""".trim()
            if (parts.isEmpty()) return null
            return "# Default skills\n\nThese skills are installed in ${Skills.DIR} and are part of how you work. Use them on your own; the user does not have to ask.\n\n" + parts.joinToString("\n")
        }
}
