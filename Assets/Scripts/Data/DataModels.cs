using System;
using System.Collections.Generic;

namespace CorporateDragon.Data
{
    [Serializable]
    public class StatDef
    {
        public string key;
        public string label;
    }

    [Serializable]
    public class StatEffect
    {
        public string key;
        public float delta;
    }

    [Serializable]
    public class LineData
    {
        public string speaker;
        public string text;
        public string ifFlag;
        public string ifNotFlag;
    }

    [Serializable]
    public class ChoiceData
    {
        public string text;
        public List<StatEffect> effects = new List<StatEffect>();
        public List<string> flags = new List<string>();
        public string memory;
    }

    [Serializable]
    public class Episode
    {
        public string id;
        public int chapter;
        public int episode;
        public int age;
        public string title;
        public string bg;
        public string charEmoji;
        public string mood;
        public List<LineData> lines = new List<LineData>();
        public List<ChoiceData> choices = new List<ChoiceData>();
        public bool endsChapter;
    }

    [Serializable]
    public class EpisodeList
    {
        public List<Episode> episodes = new List<Episode>();
    }

    [Serializable]
    public class FestivalYearOverride
    {
        public int year;
        public int month;
        public int day;
    }

    [Serializable]
    public class Festival
    {
        public string id;
        public string name;
        public string emoji;
        public int month;
        public int day;
        public List<FestivalYearOverride> byYear = new List<FestivalYearOverride>();
    }

    [Serializable]
    public class FestivalList
    {
        public List<Festival> festivals = new List<Festival>();
    }

    [Serializable]
    public class MemoryCard
    {
        public string id;
        public string name;
        public string emoji;
        public string desc;
    }

    [Serializable]
    public class MemoryCardList
    {
        public List<MemoryCard> memoryCards = new List<MemoryCard>();
    }

    [Serializable]
    public class RetainerDef
    {
        public string id;
        public string name;
        public string emoji;
        public int initialAffinity;
    }

    [Serializable]
    public class RetainerList
    {
        public List<RetainerDef> retainers = new List<RetainerDef>();
    }

    [Serializable]
    public class DailyAction
    {
        public string id;
        public string name;
        public int cost;
        public List<StatEffect> effects = new List<StatEffect>();
        public string retainer;
        public string flavor;
    }

    [Serializable]
    public class DailyActionList
    {
        public List<DailyAction> actions = new List<DailyAction>();
    }

    [Serializable]
    public class StatList
    {
        public List<StatDef> stats = new List<StatDef>();
    }
}
