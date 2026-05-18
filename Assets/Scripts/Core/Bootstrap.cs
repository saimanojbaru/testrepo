using UnityEngine;

namespace CorporateDragon.Core
{
    /// Creates the GameManager in any scene without requiring scene wiring.
    /// Works in Editor Play mode and in built APK.
    public static class Bootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.BeforeSceneLoad)]
        static void Init()
        {
            if (Object.FindFirstObjectByType<GameManager>() != null) return;
            var go = new GameObject("[CorporateDragon]");
            go.AddComponent<GameManager>();
            Object.DontDestroyOnLoad(go);
        }
    }
}
