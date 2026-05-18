using System;
using System.Collections.Generic;

namespace CorporateDragon.Core
{
    [Serializable]
    public class AlbumSnapshot
    {
        public List<string> unlocked = new List<string>();
    }

    public class MemoryAlbumManager
    {
        readonly HashSet<string> _unlocked = new HashSet<string>();
        public event Action<string> OnUnlock;
        public event Action OnChanged;

        public bool IsUnlocked(string id) => !string.IsNullOrEmpty(id) && _unlocked.Contains(id);
        public IReadOnlyCollection<string> Unlocked => _unlocked;
        public int Count => _unlocked.Count;

        public void Unlock(string id)
        {
            if (string.IsNullOrEmpty(id)) return;
            if (_unlocked.Add(id))
            {
                OnUnlock?.Invoke(id);
                OnChanged?.Invoke();
            }
        }

        public AlbumSnapshot Serialize()
        {
            var s = new AlbumSnapshot();
            s.unlocked.AddRange(_unlocked);
            return s;
        }

        public void Load(AlbumSnapshot s)
        {
            _unlocked.Clear();
            if (s == null) return;
            foreach (var id in s.unlocked) _unlocked.Add(id);
            OnChanged?.Invoke();
        }
    }
}
