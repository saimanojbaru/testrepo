using System;
using System.Collections.Generic;
using UnityEngine;
using CorporateDragon.Data;

namespace CorporateDragon.Core
{
    [Serializable]
    public class StatsSnapshot
    {
        public List<string> keys = new List<string>();
        public List<float> values = new List<float>();
    }

    public class StatManager
    {
        readonly Dictionary<string, float> _values = new Dictionary<string, float>();

        public event Action OnChanged;

        public IReadOnlyDictionary<string, float> Values => _values;

        public StatManager()
        {
            ResetDefaults();
        }

        public void ResetDefaults()
        {
            _values.Clear();
            foreach (var s in DataLoader.Stats)
            {
                _values[s.key] = s.key switch
                {
                    "familyBond" => 70f,
                    "mentalHealth" => 60f,
                    "financialWisdom" => 40f,
                    "politics" => 30f,
                    "wisdom" => 30f,
                    _ => 50f,
                };
            }
            OnChanged?.Invoke();
        }

        public float Get(string key) => _values.TryGetValue(key, out var v) ? v : 0f;

        public void Apply(IEnumerable<StatEffect> effects)
        {
            if (effects == null) return;
            foreach (var e in effects)
            {
                if (string.IsNullOrEmpty(e.key)) continue;
                if (!_values.ContainsKey(e.key)) _values[e.key] = 0f;
                _values[e.key] = Mathf.Clamp(_values[e.key] + e.delta, 0f, 100f);
            }
            OnChanged?.Invoke();
        }

        public StatsSnapshot Serialize()
        {
            var snap = new StatsSnapshot();
            foreach (var kv in _values) { snap.keys.Add(kv.Key); snap.values.Add(kv.Value); }
            return snap;
        }

        public void Load(StatsSnapshot snap)
        {
            _values.Clear();
            if (snap == null) return;
            for (int i = 0; i < snap.keys.Count && i < snap.values.Count; i++)
                _values[snap.keys[i]] = snap.values[i];
            OnChanged?.Invoke();
        }
    }
}
