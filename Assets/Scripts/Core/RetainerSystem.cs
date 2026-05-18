using System;
using System.Collections.Generic;
using UnityEngine;

namespace CorporateDragon.Core
{
    [Serializable]
    public class RetainerSnapshot
    {
        public List<string> ids = new List<string>();
        public List<int> affinity = new List<int>();
    }

    public class RetainerSystem
    {
        readonly Dictionary<string, int> _affinity = new Dictionary<string, int>();
        public event Action OnChanged;

        public IReadOnlyDictionary<string, int> All => _affinity;

        public void Initialize()
        {
            _affinity.Clear();
            foreach (var r in DataLoader.Retainers)
                _affinity[r.id] = r.initialAffinity;
            OnChanged?.Invoke();
        }

        public int GetAffinity(string id) => _affinity.TryGetValue(id, out var v) ? v : 0;

        public void Bump(string id, int delta)
        {
            if (string.IsNullOrEmpty(id)) return;
            if (!_affinity.ContainsKey(id)) _affinity[id] = 0;
            _affinity[id] = Mathf.Clamp(_affinity[id] + delta, 0, 10);
            OnChanged?.Invoke();
        }

        public RetainerSnapshot Serialize()
        {
            var s = new RetainerSnapshot();
            foreach (var kv in _affinity) { s.ids.Add(kv.Key); s.affinity.Add(kv.Value); }
            return s;
        }

        public void Load(RetainerSnapshot s)
        {
            _affinity.Clear();
            if (s == null) { Initialize(); return; }
            for (int i = 0; i < s.ids.Count && i < s.affinity.Count; i++)
                _affinity[s.ids[i]] = s.affinity[i];
            OnChanged?.Invoke();
        }
    }
}
