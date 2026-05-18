using System.Collections.Generic;
using UnityEngine;
using CorporateDragon.Data;

namespace CorporateDragon.Core
{
    public static class DataLoader
    {
        public static List<Episode> Episodes { get; private set; }
        public static List<Festival> Festivals { get; private set; }
        public static List<MemoryCard> MemoryCards { get; private set; }
        public static List<RetainerDef> Retainers { get; private set; }
        public static List<DailyAction> DailyActions { get; private set; }
        public static List<StatDef> Stats { get; private set; }

        public static bool Loaded { get; private set; }

        public static void LoadAll()
        {
            if (Loaded) return;
            Episodes = LoadList<EpisodeList>("Data/episodes")?.episodes ?? new List<Episode>();
            Festivals = LoadList<FestivalList>("Data/festivals")?.festivals ?? new List<Festival>();
            MemoryCards = LoadList<MemoryCardList>("Data/memorycards")?.memoryCards ?? new List<MemoryCard>();
            Retainers = LoadList<RetainerList>("Data/retainers")?.retainers ?? new List<RetainerDef>();
            DailyActions = LoadList<DailyActionList>("Data/actions")?.actions ?? new List<DailyAction>();
            Stats = LoadList<StatList>("Data/stats")?.stats ?? new List<StatDef>();
            Loaded = true;
            Debug.Log($"[DataLoader] Episodes={Episodes.Count} Festivals={Festivals.Count} Memories={MemoryCards.Count} Retainers={Retainers.Count} Actions={DailyActions.Count} Stats={Stats.Count}");
        }

        static T LoadList<T>(string path) where T : class
        {
            var ta = Resources.Load<TextAsset>(path);
            if (ta == null)
            {
                Debug.LogError($"[DataLoader] Missing Resources/{path}.json");
                return null;
            }
            return JsonUtility.FromJson<T>(ta.text);
        }
    }
}
