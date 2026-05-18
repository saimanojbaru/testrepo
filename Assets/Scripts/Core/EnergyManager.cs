using System;
using UnityEngine;

namespace CorporateDragon.Core
{
    [Serializable]
    public class EnergySnapshot
    {
        public float energy;
        public float maxEnergy;
        public long lastTickTicks;
    }

    public class EnergyManager
    {
        const float RegenPerHour = 20f;

        public float Energy { get; private set; } = 100f;
        public float MaxEnergy { get; private set; } = 100f;
        DateTime _lastTick = DateTime.UtcNow;

        public event Action OnChanged;

        readonly RetainerSystem _retainers;
        readonly FestivalManager _festivals;

        public EnergyManager(RetainerSystem retainers, FestivalManager festivals)
        {
            _retainers = retainers;
            _festivals = festivals;
        }

        public void Tick()
        {
            var now = DateTime.UtcNow;
            var hours = (float)(now - _lastTick).TotalHours;
            if (hours <= 0f) { _lastTick = now; return; }
            var mult = GetMultiplier();
            Energy = Mathf.Min(MaxEnergy, Energy + hours * RegenPerHour * mult);
            _lastTick = now;
            OnChanged?.Invoke();
        }

        public bool TrySpend(int cost)
        {
            if (Energy < cost) return false;
            Energy -= cost;
            OnChanged?.Invoke();
            return true;
        }

        public float GetMultiplier()
        {
            float m = 1f;
            if (_retainers != null && _retainers.GetAffinity("maa") >= 8) m += 0.35f;
            if (_festivals != null && _festivals.IsFestivalToday()) m += 0.4f;
            return m;
        }

        public EnergySnapshot Serialize() => new EnergySnapshot
        {
            energy = Energy, maxEnergy = MaxEnergy, lastTickTicks = _lastTick.Ticks,
        };

        public void Load(EnergySnapshot s)
        {
            if (s == null) return;
            Energy = s.energy;
            MaxEnergy = s.maxEnergy > 0 ? s.maxEnergy : 100f;
            _lastTick = s.lastTickTicks > 0 ? new DateTime(s.lastTickTicks, DateTimeKind.Utc) : DateTime.UtcNow;
            OnChanged?.Invoke();
        }
    }
}
