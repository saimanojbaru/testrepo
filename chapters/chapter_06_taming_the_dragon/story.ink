// Chapter 6: Slaying or Taming the Dragon (Ages 43–48)
// Resolution. Multiple endings based on accumulated wisdom + happiness + family_bond + debt.

VAR wisdom = 60
VAR happiness = 50
VAR family_bond = 70
VAR debt = 3500000
VAR savings = 800000
VAR mental_health = 65
VAR leadership = 70

-> the_pivot

=== the_pivot ===
# mood: ch6_resolution
A year on from the abyss. Coffee is hotter than it used to be.
Mentor: You've seen the dragon up close. Do you want to kill it, ride it, or walk away?
* [Change the system from inside. Director track.] ~ leadership += 18 ~ reputation += 14 -> family_legacy
* [Start my own thing. No more reporting.] ~ creativity += 22 ~ stress += 14 ~ savings -= 400000 -> family_legacy
* [Consulting. Teaching. Half time. Family time.] ~ wisdom += 14 ~ relationship_satisfaction += 16 ~ happiness += 16 -> family_legacy

=== family_legacy ===
# mood: ch6_resolution
Your child, now seventeen, slumps onto the sofa.
Child: Everyone says I should do engineering. But I want to do design.
* [Safe job first. Passion later.] ~ family_bond -= 6 ~ wisdom -= 4 -> dragon_battle
* [I followed the safe path. It cost me. Don't repeat my mistakes.] ~ family_bond += 18 ~ wisdom += 12 ~ parenting_skill += 12 -> dragon_battle
* [Here is my full story. You decide.] ~ family_bond += 22 ~ wisdom += 18 ~ parenting_skill += 16 -> dragon_battle

=== dragon_battle ===
# mood: ch5_dragon_dream
# mood_fade: 3.0
The dragon. Smaller now. Or you are larger. Or both.
You sit across from it like an old colleague at a farewell dinner.
* [I built you. I can let you go.] ~ wisdom += 20 ~ mental_health += 14 -> the_reckoning
* [You taught me. Now I leave.] ~ wisdom += 14 ~ happiness += 10 -> the_reckoning
* [I'll teach others to recognise you sooner.] ~ wisdom += 24 ~ leadership += 12 -> the_reckoning

=== the_reckoning ===
# mood: ch6_resolution
# mood_fade: 8.0
The last office goodbye. Or the first day of something new.
A child's wedding. A parent's anniversary. A book you finally read.
{
  - wisdom > 80:
      You taught yourself, then others. Some called it leadership. You called it remembering. -> ending_legacy_builder
  - happiness > 70 and family_bond > 70:
      You did not become rich. You became findable when it mattered. -> ending_balanced_fulfillment
  - savings > 5000000 and family_bond < 50:
      You won. Mostly. The mirror is hard to look at. -> ending_corporate_sovereign
  - mental_health < 35:
      The body kept the score. -> ending_tragic_regret
  - else:
      A quieter life. Smaller fights. A different kind of full. -> ending_simple_redemption
}

=== ending_legacy_builder ===
You opened a small school for kids who could not afford coaching.
Your old colleagues call sometimes. Some of them have quit.
-> END

=== ending_balanced_fulfillment ===
The flat is smaller. The mornings are slower. Your spouse is reading on the same sofa.
Your child sends you a meme. You laugh out loud.
-> END

=== ending_corporate_sovereign ===
The corner office. The car. The corporate award on the shelf.
You eat dinner alone more nights than not.
-> END

=== ending_tragic_regret ===
The doctor uses the word maintenance, not recovery.
You write a long letter to your younger self. You actually send it, to your kid.
-> END

=== ending_simple_redemption ===
You moved. New town. Slower internet. Better mangoes.
You started teaching local kids on Saturdays without quite meaning to.
-> END
