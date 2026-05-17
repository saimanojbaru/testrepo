// Chapter 3: Entering the Dragon's Den (Ages 23–27)
// The first slow burn. Politics. Crunch. Money. Marriage pressure.

VAR discipline = 82
VAR technical_skills = 62
VAR soft_skills = 58
VAR reputation = 50
VAR leadership = 0
VAR family_bond = 78
VAR financial_awareness = 48
VAR debt = 950000
VAR stress = 48
VAR happiness = 65

-> first_review

=== first_review ===
# mood: ch3_corporate
Six months in. The automation script you built has been merged.
Your skip-level adjusts his collar.
Boss: Your teammate said the script was mainly his idea. Good team effort.
* [Thank you, sir. I'll do better.] ~ reputation += 4 ~ stress += 18 ~ happiness -= 10 -> first_crunch
* [Actually sir, here is the commit history.] ~ reputation += 12 ~ soft_skills += 8 ~ stress += 6 -> first_crunch
* [Confront colleague privately later.] ~ social_charm -= 4 ~ wisdom += 4 ~ leadership += 4 -> first_crunch

=== first_crunch ===
# mood: ch3_corporate
# minigame: meeting_survival
Boss: Team, this has to go live by Sunday. Client is important.
* [I'll stay back, sir.] ~ technical_skills += 12 ~ reputation += 10 ~ stress += 25 ~ happiness -= 8 -> family_call
* [I can deliver Monday EOD if we re-prioritise.] ~ soft_skills += 10 ~ reputation += 4 ~ stress += 8 -> family_call
* [Sir, I have a family commitment this weekend.] ~ family_bond += 8 ~ happiness += 6 ~ reputation -= 12 -> family_call

=== family_call ===
# mood: ch3_corporate
Mother on the phone, against a kitchen exhaust fan.
Maa: Beta, your cousin got married last month. When are we doing yours?
* [Maa, I'm too young.] ~ family_bond -= 4 ~ happiness += 4 -> emi_pressure
* [Let me focus on work for two more years.] ~ family_bond += 2 ~ wisdom += 2 -> emi_pressure
* [Okay. Start looking.] ~ family_bond += 12 ~ stress += 8 -> emi_pressure

=== emi_pressure ===
# mood: ch3_corporate
# minigame: emi_simulator
First salary hike, ten percent. Family asks about a bigger bike. Bank suggests a personal loan.
* [Send more home. Take the loan.] ~ debt += 600000 ~ family_bond += 14 ~ happiness += 4 -> switch_offer
* [Save the hike. Build an emergency fund.] ~ financial_awareness += 16 ~ family_bond -= 4 ~ wisdom += 4 -> switch_offer
* [Balance both honestly.] ~ financial_awareness += 8 ~ family_bond += 6 ~ social_charm += 4 -> switch_offer

=== switch_offer ===
# mood: ch3_corporate
LinkedIn message. Twenty-five percent hike. New domain. Better Glassdoor reviews.
* [Stay loyal.] ~ reputation += 12 ~ happiness += 2 -> promotion
* [Switch.] ~ technical_skills += 10 ~ stress += 14 ~ reputation -= 6 -> promotion
* [Counter-offer using the email.] ~ soft_skills += 12 ~ financial_awareness += 8 -> promotion

=== promotion ===
# mood: ch3_corporate
# mood_fade: 6.0
Senior Engineer. Team Lead. Some title with three words.
You order dosa at midnight to celebrate alone.
Inner voice: I'm climbing. Why does it feel heavier?
-> END
