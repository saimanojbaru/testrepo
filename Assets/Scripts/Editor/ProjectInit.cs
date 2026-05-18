#if UNITY_EDITOR
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace CorporateDragon.EditorTools
{
    public static class ProjectInit
    {
        const string PackageId = "com.corporatedragon.odyssey";
        const string ProductName = "Corporate Dragon";
        const string CompanyName = "Corporate Dragon Studio";
        const string Version = "0.2.0";

        [MenuItem("Tools/Corporate Dragon/Configure Android Build", priority = 0)]
        public static void ConfigureAndroidBuild()
        {
            PlayerSettings.companyName = CompanyName;
            PlayerSettings.productName = ProductName;
            PlayerSettings.bundleVersion = Version;

            var ng = NamedBuildTarget.Android;
            PlayerSettings.SetApplicationIdentifier(ng, PackageId);
            PlayerSettings.SetScriptingBackend(ng, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel26;
            PlayerSettings.Android.targetSdkVersion = AndroidSdkVersions.AndroidApiLevelAuto;
            PlayerSettings.Android.bundleVersionCode = 1;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;

            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);

            Debug.Log("[CorporateDragon] Android build configured. Package=" + PackageId + " Version=" + Version);
            EditorUtility.DisplayDialog("Corporate Dragon",
                "Android build settings configured:\n\n" +
                "• Package: " + PackageId + "\n" +
                "• Version: " + Version + "\n" +
                "• Min SDK: 26 (Android 8)\n" +
                "• Backend: IL2CPP, ARM64\n" +
                "• Orientation: Portrait\n\n" +
                "Now: File → Build Settings → Build, or Build And Run to install on a device.", "OK");
        }

        [MenuItem("Tools/Corporate Dragon/Build APK", priority = 1)]
        public static void BuildApk()
        {
            ConfigureAndroidBuild();

            var scenes = ResolveScenes();
            if (scenes.Count == 0)
            {
                EditorUtility.DisplayDialog("Corporate Dragon",
                    "No scene found. Create one first:\nFile -> New Scene -> Basic (Built-in) -> Save as Assets/Scenes/Main.unity", "OK");
                return;
            }

            string outPath = EditorUtility.SaveFilePanel("Save APK", "", "CorporateDragon.apk", "apk");
            if (string.IsNullOrEmpty(outPath)) return;

            var options = new BuildPlayerOptions
            {
                scenes = scenes.ToArray(),
                locationPathName = outPath,
                target = BuildTarget.Android,
                options = BuildOptions.None,
            };
            BuildPipeline.BuildPlayer(options);
        }

        static List<string> ResolveScenes()
        {
            var list = new List<string>();
            foreach (var s in EditorBuildSettings.scenes)
                if (s.enabled) list.Add(s.path);
            if (list.Count > 0) return list;

            var active = EditorSceneManager.GetActiveScene();
            if (!string.IsNullOrEmpty(active.path)) list.Add(active.path);
            return list;
        }
    }
}
#endif
