// Chapter 5: The Abyss — Midlife Crisis (Ages 35–42)
// The slowest, most reflective chapter. The dragon is no longer a metaphor.

VAR stress = 82
VAR happiness = 38
VAR mental_health = 45
VAR wisdom = 40
VAR debt = 5500000
VAR family_bond = 68
VAR relationship_satisfaction = 52
VAR reputation = 70
VAR savings = 500000

-> wake_up_call

=== wake_up_call ===
# mood: ch5_abyss
# mood_fade: 6.0
Conference call. Chest tightens. The cursor stops blinking.
Someone says your name three times before you hear it.
Doctor: Panic attack. Lifestyle change is not optional now.
* [Push through with medication.] ~ stress += 25 ~ mental_health -= 20 -> ai_disruption
* [Medical leave. Reflect.] ~ mental_health += 15 ~ reputation -= 15 ~ wisdom += 12 -> ai_disruption
* [Hybrid. Lifestyle while working.] ~ discipline += 10 ~ mental_health += 6 -> ai_disruption

=== ai_disruption ===
# mood: ch5_abyss
All-hands. Slide 14. "Automation Roadmap".
Boss: We need to do more with less. AI will handle routine work.
* [I'll learn the new tools immediately, sir.] ~ technical_skills += 15 ~ stress += 20 -> family_peak
* [What if we use AI to free us for innovation?] ~ creativity += 18 ~ leadership += 10 ~ wisdom += 6 -> family_peak
* [Quietly explore exit options.] ~ creativity += 12 ~ wisdom += 10 ~ stress += 8 -> family_peak

=== family_peak ===
# mood: ch5_abyss
The kitchen at 11 PM. Spouse across the table. A teenager's door closed.
Spouse: You're physically here but mentally absent for years. I'm tired.
* [I did all this for us!] ~ relationship_satisfaction -= 20 ~ stress += 15 -> dragon_dream
* [You're right. I don't know how to fix this anymore.] ~ relationship_satisfaction += 18 ~ wisdom += 12 ~ mental_health += 8 -> dragon_dream
* [Maybe I should quit.] ~ relationship_satisfaction += 8 ~ stress += 12 ~ wisdom += 10 -> dragon_dream

=== dragon_dream ===
# mood: ch5_dragon_dream
# mood_fade: 4.0
You dream of a dragon made of paper rupees and ringing clocks.
It is beautiful. It eats your years one by one and asks for more.
You wake at 4:11 AM.
-> rock_bottom

=== rock_bottom ===
# mood: ch5_abyss
Layoff letter. Hospital bill. A teenager's school complaint. Same week.
# memory_echo: stillness
* [Accept any job. Stability first.] ~ reputation += 6 ~ wisdom -= 4 ~ happiness -= 14 -> abyss_end
* [Quit. Sabbatical. Therapy. Pivot.] ~ savings -= 300000 ~ mental_health += 20 ~ wisdom += 20 ~ stress -= 18 -> abyss_end
* [Start the side business you've talked about for years.] ~ creativity += 25 ~ stress += 15 ~ wisdom += 15 -> abyss_end
* [Sell the city flat. Move smaller.] ~ debt -= 2000000 ~ stress -= 22 ~ wisdom += 18 -> abyss_end

=== abyss_end ===
# mood: ch5_abyss
# mood_fade: 8.0
A crossroads. Literal: highway, exit signs in two languages.
Symbolic: a phone call to your father in your head that you've rehearsed for years.
The dragon is smaller. Or maybe you have grown.
-> END
