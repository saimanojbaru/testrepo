// Chapter 2: Engineering the Dream (Ages 16–22)
// College life → first job. Hope curdling into the first whiff of corporate reality.

VAR discipline = 75
VAR technical_skills = 35
VAR soft_skills = 40
VAR family_bond = 78
VAR financial_awareness = 28
VAR debt = 0
VAR stress = 35
VAR happiness = 78
VAR social_charm = 60

-> college_orientation

=== college_orientation ===
# mood: ch2_college
Suitcase. Hostel block. Smell of phenyl and old books.
A senior with a clipboard squints at you.
Senior: Oye fresher! Sing a song or do 50 push-ups.
* [Sing softly.] ~ discipline += 8 ~ family_bond += 3 ~ happiness -= 4 -> first_semester
* [Only if you join me, sir!] ~ social_charm += 10 ~ happiness += 4 -> first_semester
* [This is stupid. I'm leaving.] ~ social_charm -= 8 ~ discipline += 4 -> first_semester

=== first_semester ===
# mood: ch2_college
Lectures blur. The canteen samosas are warm. You learn what "attendance shortage" means.
Mid-sem results arrive. You are nowhere near top, nowhere near failing.
* [Grind harder for next sem.] ~ discipline += 8 ~ technical_skills += 6 ~ happiness -= 3 -> first_crush
* [Get into a club. Make friends.] ~ social_charm += 10 ~ happiness += 6 -> first_crush
* [Try a side project on your laptop.] ~ technical_skills += 12 ~ creativity += 6 -> first_crush

=== first_crush ===
# mood: ch2_college
A face across the library. A casual hello. A WhatsApp.
Time you don't have to spend is the only kind worth spending.
Crush: We study only on weekends? I feel you're too busy.
* [Studies first. Sorry.] ~ technical_skills += 8 ~ happiness -= 8 ~ discipline += 4 -> education_loan
* [Let's make time. Life isn't only marks.] ~ happiness += 14 ~ social_charm += 6 ~ discipline -= 4 -> education_loan
* [I'm confused. Can we slow down?] ~ happiness += 4 ~ wisdom += 2 -> education_loan

=== education_loan ===
# mood: ch2_college
Third year. The fee structure has gone up. Papa calls.
Papa: Beta, we're considering a loan. Just for the placement coaching.
* [Take the bank loan. I'll repay it from my first job.] ~ debt += 900000 ~ financial_awareness += 8 ~ family_bond += 4 -> placements
* [I'll do part-time tuition. No loan.] ~ financial_awareness += 14 ~ discipline += 8 ~ happiness -= 8 ~ technical_skills -= 4 -> placements
* [Ask chacha for help.] ~ family_bond += 2 ~ financial_awareness += 4 -> placements

=== placements ===
# mood: ch2_college
Placement season. Pressed shirts. Aptitude tests at 8 AM.
Interviewer: 5.2 LPA. Bangalore. Joining next month.
* [Accept, with thanks.] ~ technical_skills += 12 ~ happiness += 18 ~ stress += 6 -> first_day
* [Hold out for a better offer.] ~ stress += 18 ~ technical_skills += 8 ~ financial_awareness += 4 -> first_day
* [Reject. Try a non-IT path.] ~ creativity += 14 ~ stress += 12 ~ social_charm += 4 -> first_day

=== first_day ===
# mood: ch3_corporate
# mood_fade: 6.0
A shiny ID card. A cubicle with someone else's nameplate still under the glass.
A team lunch where everyone is louder than they need to be.
Inner voice: This is it. My new life.
-> END
