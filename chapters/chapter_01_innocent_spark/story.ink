// Corporate Dragon — Chapter 1: The Innocent Spark
// Authoritative narrative source. Edit this file; recompile with `inklecate
// story.ink -o story.json` to produce the runtime-loaded JSON.
// A simplified story.fallback.json is hand-maintained in parallel until the
// inkgd addon is installed.

VAR discipline = 40
VAR curiosity = 50
VAR creativity = 45
VAR social_charm = 50
VAR family_bond = 70
VAR happiness = 85
VAR financial_awareness = 10

-> morning_routine

=== morning_routine ===
# mood: ch1_innocent
# sfx: pressure_cooker_whistle
Maa: Beta, uth jao! School ki bus aane wali hai.
Maa hands you a steel tiffin box, still warm.
Maa: Aloo paratha. Tumhara favourite.
* [Thanks Maa!]            ~ family_bond += 3   ~ happiness += 2   -> papa_asks_about_test
* [Not aloo paratha again?]  ~ social_charm += 2 ~ family_bond -= 2 -> papa_asks_about_test

=== papa_asks_about_test ===
Papa lowers his newspaper.
Papa: Aaj test hai na? Tayyari ho gayi?
* [I'll make you proud, Papa!]
    ~ discipline += 12
    ~ happiness -= 4
    # trait: people_pleaser
    -> walk_to_school
* [I studied... mostly.]
    ~ discipline += 4
    ~ social_charm += 3
    # trait: balanced
    -> walk_to_school
* [I want to invent something new today!]
    ~ creativity += 10
    ~ curiosity += 6
    ~ discipline -= 4
    # trait: dreamer
    -> walk_to_school

=== walk_to_school ===
# mood: ch1_innocent
The morning is warm. Pollen drifts through slanted sunlight.
You walk to the bus stop, tiffin clutched tight.
-> first_big_test

=== first_big_test ===
# mood: ch1_exam_focus
# mood_fade: 2.0
Exam Hall. Ceiling fan ticks overhead. Pencils scratch.
# minigame: exam_puzzle
-> power_cut_study_night

=== power_cut_study_night ===
# mood: ch1_power_cut
# mood_fade: 6.0
The lights cut out at 8:47 PM.
You light the emergency lamp. Moths gather almost instantly.
Maa: Beta, doodh leke aayi hoon. Thoda break le lo.
# memory_echo: parent_sacrifice
-> family_financial_strain

=== family_financial_strain ===
# mood: ch1_family_strain
# mood_fade: 5.0
Papa is sitting on the floor with the household account book.
Maa is folding clothes nearby, very quietly.
Papa: Is mahine thoda tight hai. Naya school bag agle mahine.
* [I understand. I'll study harder so I get scholarship.]
    ~ family_bond += 15
    ~ discipline += 8
    ~ financial_awareness += 5
    -> chapter_end
* [Okay, Papa.] [Stays silent, sad.]
    ~ happiness += 6
    ~ family_bond -= 2
    -> chapter_end
* [Papa, can we start a small shop like uncle?]
    ~ curiosity += 12
    ~ financial_awareness += 10
    ~ creativity += 5
    # trait: early_entrepreneur
    -> chapter_end

=== chapter_end ===
# mood: ch1_innocent
# mood_fade: 8.0
Years pass in a blur of textbooks, tuition classes, and evenings on the
balcony watching kites cut each other down.
At 15, you crack the entrance exam.
The family hugs you. Maa cries. Papa pretends he isn't.
-> END
