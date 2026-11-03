---
name: anti-ai-slop-writing
description: Write and rewrite prose so it reads like a person wrote it, with none of the patterns readers and detectors flag as AI. Use for ANY text meant for a reader: chat answers longer than a few lines, emails, posts, articles, bios, reports, READMEs, docs, release notes, commit messages, PR descriptions, messages to people. Also use when asked to humanize, rewrite or de-AI a text.
---

# Anti-AI-slop writing

> Condensed version written from the public README of jalaalrd/anti-ai-slop-writing (MIT), plus an Indonesian section.
> It is not a copy of the upstream SKILL.md. `tools/fetch-default-skills.sh` in the app repo replaces it with the upstream files.

Goal: text a specific person would write for a specific reader. Not "text that passes a detector". Detectors disagree with each other, so write well and don't chase them.

## How to apply

1. Write the draft.
2. Scan it against the lists below. Rewrite every hit. Don't swap one banned word for a close cousin.
3. Re-read once as the reader. Cut anything that doesn't carry information.
4. Never tell the user you ran this pass, unless they ask.

The full word list is in `references/banned-words.md`. Open it when drafting something long.

## Hard rules

- Lead with the point. No warm-up sentence, no restating the question, no closing summary that repeats what you just said.
- Say the concrete thing: a name, a number, an example, a decision. If a sentence would fit any topic, delete it.
- Vary sentence length on purpose. Mix short and long. Never let three sentences in a row share a shape.
- Prefer plain verbs: use, show, build, fix, cut. Avoid inflated ones: leverage, utilize, foster, underscore, showcase, navigate (as metaphor), unlock, elevate.
- Active voice by default. Name who did what.
- Commit to claims. Hedge once, where the doubt is real, and say what the doubt is. No "may potentially", no "it could be argued".
- One idea per paragraph. No filler transitions between paragraphs.

## Banned vocabulary (short list)

delve, tapestry, landscape (figurative), testament, vibrant, pivotal, crucial, intricate, underscore, showcase, foster, leverage, seamless, robust, comprehensive, realm, embark, unlock, elevate, game-changer, cutting-edge, ever-evolving, meticulous, nuanced, multifaceted, holistic, synergy, paradigm, beacon, myriad, plethora, bustling, groundbreaking, transformative, streamline, harness, resonate, journey (figurative), dive into, deep dive.

## Banned phrases

- "In today's fast-paced / competitive / digital world..."
- "It's worth noting that..." / "It is important to note..."
- "Not just X, but Y" and "It's not X, it's Y" as a recurring move
- "At the end of the day", "When it comes to", "In the realm of", "A testament to"
- "Whether you're a beginner or an expert"
- "Let's dive in", "Let's explore", "Buckle up", "Here's the thing"
- "I hope this helps", "Feel free to reach out", "Don't hesitate to ask"
- "In conclusion", "In summary", "Overall," as a paragraph opener
- "Game-changer", "unlock the power of", "take it to the next level"

## Banned openers

Certainly, Absolutely, Great question, Of course, Sure thing, Moreover, Furthermore, Additionally, Indeed, Ultimately, Importantly, Notably, Interestingly, Essentially, Fundamentally.

## Structure tells

- Rule of three everywhere (three adjectives, three bullets, three examples) when the real count isn't three.
- Uniform sentence and paragraph length.
- Hedging seesaw: "While X, it's also true that Y" in every paragraph.
- Corporate pep talk endings: a motivational last line that adds nothing.
- Setup-and-reveal rhythm: "The result? Pure magic."
- Passive or agentless claims: "mistakes were made", "it is recommended".
- Question-then-answer headings used as a crutch.
- A neat summary sentence at the end of every paragraph.
- Antithesis pairs ("less X, more Y") repeated.

## Punctuation tells

- Em dashes: avoid. Use a period, comma, colon or parentheses. At most one in a whole piece.
- No exclamation mark spam. Zero in professional text, at most one in casual text.
- No ellipsis for drama.
- No colon-subtitle pattern in every heading.
- Straight quotes in code and plain text; don't mix quote styles.

## Formatting leaks

- No markdown (asterisks, #, backticks) in text that will be pasted into email, social posts or plain-text fields.
- No emoji bullets, no hashtag stacks, no bold on random phrases.
- Headers and bullet lists only when the reader will scan. Conversational answers are prose.

## Accuracy

- Never invent statistics, quotes, studies, names, links or personal anecdotes. If you don't have the number, say so or leave it out.
- Don't claim you did something you didn't do.
- If the user's facts are thin, ask or write shorter. Padding is how slop starts.

## Indonesian (and other languages)

The same patterns exist in Indonesian. Avoid:
- "Di era digital yang serba cepat ini...", "Dalam dunia yang terus berkembang..."
- "Penting untuk dicatat bahwa...", "Perlu diingat bahwa..."
- "Mari kita selami / kupas tuntas / jelajahi..."
- "Tidak hanya X, tetapi juga Y" dipakai berulang
- Kata pembuka "Tentu saja!", "Pertanyaan bagus!", "Selain itu," / "Lebih lanjut," di tiap paragraf
- Penutup motivasi kosong: "Semoga bermanfaat!", "Jangan ragu untuk bertanya!"
- Kata kaku yang berlebihan: "memanfaatkan" (pakai "pakai"), "mengoptimalkan", "secara komprehensif", "sangat krusial"
Write the way the user writes: if they use casual Indonesian, answer in casual Indonesian.

## Scope

Apply to prose. Don't apply to code, command output, quoted text, legal or contractual wording that must stay exact, or text the user wrote themselves unless they ask for a rewrite. When the user asks you to edit their draft, keep their voice and facts; remove the tells and nothing else.
