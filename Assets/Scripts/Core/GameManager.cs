using System.Collections.Generic;
using UnityEngine;
using CorporateDragon.Data;
using CorporateDragon.UI;

namespace CorporateDragon.Core
{
    public class GameManager : MonoBehaviour
    {
        public static GameManager I { get; private set; }

        public StatManager Stats;
        public RetainerSystem Retainers;
        public ChoiceTracker Choices;
        public MemoryAlbumManager Album;
        public FestivalManager Festivals;
        public EnergyManager Energy;
        public AdaptiveEventSystem Adaptive;
        public DialogueManager Dialogue;

        public int CurrentEpisodeIdx;
        public bool Started;
        public readonly List<string> Log = new List<string>();

        UIRoot _ui;

        void Awake()
        {
            if (I != null && I != this) { Destroy(gameObject); return; }
            I = this;
            DataLoader.LoadAll();

            Retainers = new RetainerSystem();
            Festivals = new FestivalManager();
            Stats = new StatManager();
            Choices = new ChoiceTracker();
            Album = new MemoryAlbumManager();
            Energy = new EnergyManager(Retainers, Festivals);
            Adaptive = new AdaptiveEventSystem(Choices);
            Dialogue = new DialogueManager(Adaptive, Choices, Stats, Retainers, Album);

            Retainers.Initialize();

            LoadGame();

            _ui = UIRoot.Create();
            _ui.ShowTitle();
        }

        void Update()
        {
            Energy?.Tick();
        }

        public void StartNewRun()
        {
            SaveManager.Delete();
            Stats.ResetDefaults();
            Retainers.Initialize();
            Choices = new ChoiceTracker();
            Album = new MemoryAlbumManager();
            Energy = new EnergyManager(Retainers, Festivals);
            Adaptive = new AdaptiveEventSystem(Choices);
            Dialogue = new DialogueManager(Adaptive, Choices, Stats, Retainers, Album);
            CurrentEpisodeIdx = 0;
            Started = true;
            Log.Clear();
            SaveGame();
        }

        public void ContinueRun()
        {
            Started = true;
            SaveGame();
        }

        public Episode CurrentEpisode =>
            (CurrentEpisodeIdx >= 0 && CurrentEpisodeIdx < DataLoader.Episodes.Count)
                ? DataLoader.Episodes[CurrentEpisodeIdx] : null;

        public void AdvanceAfterEpisode()
        {
            CurrentEpisodeIdx++;
            SaveGame();
        }

        public void AppendLog(string entry)
        {
            Log.Insert(0, entry);
            if (Log.Count > 30) Log.RemoveAt(Log.Count - 1);
        }

        public void SaveGame()
        {
            var data = new SaveData
            {
                currentEpisodeIdx = CurrentEpisodeIdx,
                started = Started,
                stats = Stats.Serialize(),
                energy = Energy.Serialize(),
                retainers = Retainers.Serialize(),
                choices = Choices.Serialize(),
                album = Album.Serialize(),
                log = new List<string>(Log),
            };
            SaveManager.Save(data);
        }

        public bool LoadGame()
        {
            var data = SaveManager.Load();
            if (data == null) return false;
            CurrentEpisodeIdx = data.currentEpisodeIdx;
            Started = data.started;
            Stats.Load(data.stats);
            Energy.Load(data.energy);
            Retainers.Load(data.retainers);
            Choices.Load(data.choices);
            Album.Load(data.album);
            Log.Clear();
            if (data.log != null) Log.AddRange(data.log);
            return true;
        }
    }
}
