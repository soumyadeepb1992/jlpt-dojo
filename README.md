# JLPT Dojo 日本語道場

An offline Android app for practising Japanese vocabulary and kanji from JLPT N5 to N1, built with Kotlin and Jetpack Compose. Targets Android 15 (API 35), runs on Android 8.0+, tuned for phones like the Galaxy S25 Ultra.

## What's inside

**Vocabulary Battle**: 8,023 words across N5–N1, three answer choices per card.
- Question types: Word → Meaning, Meaning → Word, Word → Reading (kana)
- Words per game: 10 / 20 / 30 / 50 / 100 / All
- Game modes: Classic, Survival (3 hearts), Time Attack (90 s)

**Kanji Battle**: 2,211 kanji, same modes and deck sizes.
- Question types: Kanji → Meaning, Kanji → On'yomi, Kanji → Kun'yomi, Meaning → Kanji
- Distractors are chosen to be plausible (similar reading length/first sound, similar stroke count, same word type)

**Kanji Library**: browse by level, search by kanji, meaning or reading.
- Each kanji shows meanings, stroke count, on'yomi (カタカナ) and kun'yomi (ひらがな, okurigana in brackets)
- Example words for each reading type, with the kanji highlighted, reading, meaning and JLPT level
- Mark kanji as learned; progress per level

**Word List**: searchable list of every word, with a "Missed" filter.

**Gamification**: XP, combo multiplier, 8 ranks (初心者 → 名人), daily streak, 1–3 stars per game, personal bests per setup, confetti, haptics, and a review list. Anything you get wrong comes up more often until you answer it correctly, and you can replay just the missed items.

**Pronunciation**: 🔊 buttons use the phone's text-to-speech. If they don't appear, install a Japanese voice: Settings → General management → Text-to-speech (choose Google or Samsung engine) → install Japanese voice data.

Everything works offline; progress is saved on the phone.

## Install on your Galaxy S25 Ultra

### Option A: Android Studio (recommended)
1. Install Android Studio (https://developer.android.com/studio).
2. File → Open → select this `JlptDojo` folder. Let Gradle sync finish (first time downloads the build tools).
   If Studio offers to upgrade the Android Gradle Plugin, you can accept or skip; both work.
3. On the phone: Settings → About phone → Software information → tap **Build number** 7 times. Then Settings → Developer options → turn on **USB debugging**.
4. Connect the phone by USB, accept the prompt on the phone, choose it in the device dropdown and press ▶ Run.

To get an installable file instead: Build → Build App Bundle(s) / APK(s) → Build APK(s). The APK appears in `app/build/outputs/apk/debug/app-debug.apk`; copy it to the phone and open it (allow "Install unknown apps" when asked).

### Option B: Build in the cloud with GitHub (no Android Studio)
Push this folder to a GitHub repository. The included workflow (`.github/workflows/build-apk.yml`) builds the APK automatically; download it from the run's **Artifacts** section on the Actions tab and install it on the phone.

## Project layout
```
app/src/main/
  assets/vocab.json, kanji.json     word + kanji data (generated)
  java/com/jlptdojo/app/
    MainActivity.kt
    data/   JlptData (models + loading), Progress (saved stats), Speaker (TTS)
    game/   Quiz.kt: modes, question generation, game session, results
    ui/     Home, Setup, Quiz, Result, Kanji library/detail, Word list, theme
tools/build_data.py                 regenerates the JSON assets
```

## Data sources and credits
- Vocabulary: JLPT word lists compiled by Jonathan Waller (tanos.co.uk), via [open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks) (MIT).
- Kanji: [kanji-data](https://github.com/davidluzgouveia/kanji-data) (MIT), built on KANJIDIC (EDRDG) and WaniKani meanings; levels use the post-2010 N5–N1 assignment.
- On'yomi/kun'yomi example words are matched automatically by aligning each word's reading against its kanji readings, so a few examples may be imperfect, and some rare N1 kanji have no example in the word list.

The JLPT has not published official word lists since 2010; these are the widely used community lists.
