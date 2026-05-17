// Chapter 4: The Middle-Class Trap (Ages 28–34)
// Marriage. First child. Home loan. The cage is built one good decision at a time.

VAR leadership = 48
VAR reputation = 65
VAR debt = 1800000
VAR savings = 200000
VAR stress = 68
VAR happiness = 52
VAR family_bond = 72
VAR relationship_satisfaction = 65
VAR parenting_skill = 0
VAR financial_awareness = 65
VAR wisdom = 8

-> wedding_year

=== wedding_year ===
# mood: ch4_trap
The wedding photos are already framed. The honeymoon is one suitcase away.
Spouse: You promised you'd be home by 8. Third time this week.
* [Deadlines are tight. Try to understand.] ~ relationship_satisfaction -= 18 ~ reputation += 6 ~ stress += 10 -> home_purchase
* [I'm sorry. Let's plan tomorrow together.] ~ relationship_satisfaction += 14 ~ happiness += 8 -> home_purchase
* [I hate this job sometimes. But we need the money.] ~ relationship_satisfaction += 6 ~ wisdom += 4 ~ happiness -= 4 -> home_purchase

=== home_purchase ===
# mood: ch4_trap
# minigame: budget_allocator
A 2BHK in a half-finished tower. The builder smiles too much.
* [Aggressive loan. Big house. Looks good for family.] ~ debt += 5500000 ~ family_bond += 6 ~ stress += 14 -> first_child
* [Modest home. Keep some buffer.] ~ debt += 3500000 ~ financial_awareness += 12 ~ wisdom += 4 -> first_child
* [Keep renting. Invest the difference.] ~ savings += 800000 ~ financial_awareness += 20 ~ family_bond -= 8 -> first_child

=== first_child ===
# mood: ch4_trap
Tiny fingers. A song you hum without remembering where you learned it.
And then back to the laptop because the on-call rotation does not pause.
* [Take parental leave seriously.] ~ parenting_skill += 18 ~ reputation -= 8 ~ happiness += 12 -> team_lead
* [Just work harder. Provide better.] ~ parenting_skill -= 4 ~ reputation += 10 ~ relationship_satisfaction -= 10 -> team_lead
* [Negotiate flexible hours.] ~ parenting_skill += 10 ~ soft_skills += 8 ~ reputation += 2 -> team_lead

=== team_lead ===
# mood: ch4_trap
You manage three engineers now. One is brilliant and burning out. One is invisible. One is loud.
* [Push the team for results.] ~ leadership += 12 ~ reputation += 14 ~ wisdom -= 4 -> health_warning
* [Supportive: ask each one what they need.] ~ leadership += 16 ~ soft_skills += 10 ~ reputation += 4 -> health_warning
* [Play politics. Favour boss's favourites.] ~ reputation += 18 ~ wisdom -= 8 ~ happiness -= 8 -> health_warning

=== health_warning ===
# mood: ch4_balcony
# mood_fade: 8.0
Doctor's office. Beige tube light. Numbers on a printout.
Doctor: Your BP and sugar are elevated. Stress is the main issue.
* [Doctor, I have EMIs and responsibilities.] ~ stress += 18 ~ mental_health -= 12 -> trap_closes
* [What would actually change my life?] ~ wisdom += 12 ~ mental_health += 4 -> trap_closes
* [Lifestyle changes. I'll try.] ~ discipline += 8 ~ stress -= 4 -> trap_closes

=== trap_closes ===
# mood: ch4_balcony
# mood_fade: 8.0
The new balcony. Loan statement in one hand. Family photo wallpaper on the phone in the other.
Inner voice: I have everything I was supposed to want. Why does it feel like a golden cage?
-> END
