using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace CorporateDragon.Core
{
    [Serializable]
    public class SaveData
    {
        public int currentEpisodeIdx;
        public bool started;
        public StatsSnapshot stats;
        public EnergySnapshot energy;
        public RetainerSnapshot retainers;
        public ChoiceSnapshot choices;
        public AlbumSnapshot album;
        public List<string> log = new List<string>();
    }

    public static class SaveManager
    {
        const string FileName = "save_v1.json";

        static string FilePath => Path.Combine(Application.persistentDataPath, FileName);

        public static void Save(SaveData data)
        {
            try
            {
                var json = JsonUtility.ToJson(data, false);
                File.WriteAllText(FilePath, json);
            }
            catch (Exception e) { Debug.LogError("[SaveManager] Save failed: " + e.Message); }
        }

        public static SaveData Load()
        {
            try
            {
                if (!File.Exists(FilePath)) return null;
                var json = File.ReadAllText(FilePath);
                return JsonUtility.FromJson<SaveData>(json);
            }
            catch (Exception e) { Debug.LogError("[SaveManager] Load failed: " + e.Message); return null; }
        }

        public static void Delete()
        {
            try { if (File.Exists(FilePath)) File.Delete(FilePath); }
            catch (Exception e) { Debug.LogError("[SaveManager] Delete failed: " + e.Message); }
        }
    }
}
