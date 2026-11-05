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
    /** Meta skill of the engineering set: it says which of the others applies to the task at hand. */
    const val ENGINEERING = "using-agent-skills"
    /** Shared checklists the engineering skills link to as ../_references/ (no SKILL.md, so it is not listed as a skill). */
    private const val SHARED = "_references"
    private val engineering = listOf(
        ENGINEERING, "interview-me", "idea-refine", "spec-driven-development", "constraint-driven-development",
        "planning-and-task-breakdown", "incremental-implementation", "test-driven-development", "context-engineering",
        "source-driven-development", "doubt-driven-development", "frontend-ui-engineering", "api-and-interface-design",
        "browser-testing-with-devtools", "debugging-and-error-recovery", "code-review-and-quality", "code-simplification",
        "security-and-hardening", "performance-optimization", "git-workflow-and-versioning", "ci-cd-and-automation",
        "deprecation-and-migration", "documentation-and-adrs", "observability-and-instrumentation", "shipping-and-launch",
    )
    const val CAVEMAN = "caveman"
    const val PONYTAIL = "ponytail"
    const val SUPERPOWERS = "using-superpowers"
    /** Licenses and notices of the bundled third-party skills (no SKILL.md, so not listed as a skill). */
    private const val LICENSES = "_licenses"
    private val caveman = listOf(CAVEMAN, "ultracave", "megacave", "caveman-commit", "caveman-review", "caveman-help")
    private val ponytail = listOf(PONYTAIL, "ponytail-review", "ponytail-audit", "ponytail-debt", "ponytail-gain", "ponytail-help")
    private val superpowers = listOf(
        SUPERPOWERS, "brainstorming", "writing-plans", "executing-plans", "subagent-driven-development",
        "dispatching-parallel-agents", "systematic-debugging", "test-driven-development", "verification-before-completion",
        "requesting-code-review", "receiving-code-review", "using-git-worktrees", "finishing-a-development-branch",
        "writing-skills", "diagnosing-superpowers",
    )
    private val all = listOf(SLOP, REA, SHARED, LICENSES) + engineering + caveman + ponytail + superpowers

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
            if (installed(ENGINEERING)) parts += """
- $ENGINEERING: for software work (a feature, a bug, a review, a refactor, a release), load this skill first. It maps the task to the one skill that fits (spec, plan, build, test, debug, review, ship) in ${Skills.DIR}; then load that skill and follow its process. Skip it for small questions and one-line edits.
""".trim()
            if (installed(CAVEMAN)) parts += """
- $CAVEMAN is ON for every chat reply, from the first message, until the user says "stop caveman" or "normal mode" (switching it on again: /caveman). Apply silently, never announce it: answer first, then reason, then next step; no greeting, hedging, recap or closer; short words; articles optional but never drop not/never/no/only; one idea per sentence; code, commands, paths, numbers and errors verbatim; same language as the user. Use plain full sentences for security warnings, irreversible actions, questions to the user, and anything saved outside the chat (code, comments, commits, docs, README, PR text). When writing such text, $SLOP still applies. Details: load the skill.
""".trim()
            if (installed(PONYTAIL)) parts += """
- $PONYTAIL is ON for every coding task (write, fix, refactor, review, choose a dependency), level full, until the user says "stop ponytail" or "normal mode". Make the smallest change that fully solves the task: skip what was not asked, reuse what the codebase and standard library already have, add no dependency or abstraction for a few lines, but finish every caller, test and fixture the change touches and never cut validation, error handling or security. End the reply with one or two lines: what you skipped or did not check, and any risk. Load the skill for the full rules.
""".trim()
            if (installed(SUPERPOWERS)) parts += """
- $SUPERPOWERS: at the start of every conversation load this skill, and before every response or action check whether a Superpowers skill applies (even a 1% chance means load it and follow it). Process skills come first: brainstorming before building anything new, writing-plans for multi-step work, systematic-debugging for bugs and failures, test-driven-development when writing code, verification-before-completion before saying anything is done. "superpowers:NAME" means the skill called NAME. In OpenCode, the todo list is the todowrite tool and subagents are the task tool. Skip this for plain questions and one-line edits. The user's own instructions always win over a skill. If a skill and the engineering set ($ENGINEERING) both fit, follow the Superpowers process and use the engineering skill for its domain checklist.
""".trim()
            if (parts.isEmpty()) return null
            return "# Default skills\n\nThese skills are installed in ${Skills.DIR} and are part of how you work. Use them on your own; the user does not have to ask.\n\n" + parts.joinToString("\n")
        }
}
