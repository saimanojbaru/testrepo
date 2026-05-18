using System;
using System.Collections.Generic;
using CorporateDragon.Data;

namespace CorporateDragon.Core
{
    public class FestivalManager
    {
        public struct UpcomingFestival
        {
            public Festival festival;
            public DateTime date;
            public int daysAway;
        }

        public bool IsFestivalToday()
        {
            var today = DateTime.Now.Date;
            foreach (var f in DataLoader.Festivals)
            {
                var (m, d) = DateForYear(f, today.Year);
                if (m == today.Month && Math.Abs(d - today.Day) <= 1) return true;
            }
            return false;
        }

        public List<UpcomingFestival> Upcoming(int limit)
        {
            var today = DateTime.Now.Date;
            var list = new List<UpcomingFestival>();
            foreach (var f in DataLoader.Festivals)
            {
                var dt = NextOccurrence(f, today);
                list.Add(new UpcomingFestival
                {
                    festival = f,
                    date = dt,
                    daysAway = (int)(dt - today).TotalDays,
                });
            }
            list.Sort((a, b) => a.date.CompareTo(b.date));
            if (list.Count > limit) list = list.GetRange(0, limit);
            return list;
        }

        public DateTime NextOccurrence(Festival f, DateTime today)
        {
            int year = today.Year;
            var (m, d) = DateForYear(f, year);
            var dt = SafeDate(year, m, d);
            if (dt < today)
            {
                year += 1;
                (m, d) = DateForYear(f, year);
                dt = SafeDate(year, m, d);
            }
            return dt;
        }

        static (int month, int day) DateForYear(Festival f, int year)
        {
            if (f.byYear != null)
            {
                foreach (var o in f.byYear)
                    if (o.year == year) return (o.month, o.day);
            }
            return (f.month, f.day);
        }

        static DateTime SafeDate(int year, int month, int day)
        {
            int dim = DateTime.DaysInMonth(year, month);
            return new DateTime(year, month, Math.Min(day, dim));
        }

        public static string WhenLabel(int daysAway, DateTime dt)
        {
            if (daysAway == 0) return "Today!";
            if (daysAway == 1) return "Tomorrow";
            if (daysAway < 30) return $"in {daysAway} days";
            if (daysAway < 365) return dt.ToString("MMM d");
            return dt.ToString("MMM d, yyyy");
        }
    }
}
