using UnityEngine;
using UnityEngine.UI;
using CorporateDragon.Core;

namespace CorporateDragon.UI
{
    public class TitleScreen : MonoBehaviour
    {
        Button _start;
        Button _continue;
        Button _reset;
        Text _continueLabel;

        public static TitleScreen Build(Transform parent)
        {
            var go = UIHelpers.Panel("TitleScreen", parent, UIHelpers.Bg);
            var ts = go.AddComponent<TitleScreen>();

            var titleText = UIHelpers.Label("Title", go.transform, "Corporate\nDRAGON", 140, UIHelpers.WarmDeep, TextAnchor.MiddleCenter);
            titleText.fontStyle = FontStyle.Bold;
            var trt = titleText.GetComponent<RectTransform>();
            trt.anchorMin = new Vector2(0, 0.55f);
            trt.anchorMax = new Vector2(1, 0.85f);
            trt.offsetMin = Vector2.zero;
            trt.offsetMax = Vector2.zero;

            var subtitle = UIHelpers.Label("Subtitle", go.transform, "A Middle-Class Odyssey", 48, UIHelpers.InkSoft, TextAnchor.MiddleCenter);
            subtitle.fontStyle = FontStyle.Italic;
            var srt = subtitle.GetComponent<RectTransform>();
            srt.anchorMin = new Vector2(0, 0.48f);
            srt.anchorMax = new Vector2(1, 0.55f);
            srt.offsetMin = Vector2.zero;
            srt.offsetMax = Vector2.zero;

            ts._start = UIHelpers.SolidButton("Start", go.transform, "Begin Story", UIHelpers.WarmDeep, Color.white);
            PlaceBtn(ts._start, 0.30f, 0.38f);
            ts._start.onClick.AddListener(() =>
            {
                GameManager.I.StartNewRun();
                FindFirstObjectByType<UIRoot>().ShowHome();
            });

            ts._continue = UIHelpers.SolidButton("Continue", go.transform, "Continue", UIHelpers.Warm, UIHelpers.Ink);
            PlaceBtn(ts._continue, 0.22f, 0.30f);
            ts._continueLabel = ts._continue.GetComponentInChildren<Text>();
            ts._continue.onClick.AddListener(() =>
            {
                GameManager.I.ContinueRun();
                FindFirstObjectByType<UIRoot>().ShowHome();
            });

            ts._reset = UIHelpers.SolidButton("Reset", go.transform, "Reset Save", UIHelpers.Card, UIHelpers.InkSoft);
            PlaceBtn(ts._reset, 0.14f, 0.22f);
            ts._reset.onClick.AddListener(() =>
            {
                GameManager.I.StartNewRun();
                GameManager.I.Started = false;
                GameManager.I.SaveGame();
                ts.Refresh();
            });

            var footer = UIHelpers.Label("Footer", go.transform, "Prototype v0.2 — Unity 6 build", 28, UIHelpers.InkSoft, TextAnchor.MiddleCenter);
            var frt = footer.GetComponent<RectTransform>();
            frt.anchorMin = new Vector2(0, 0.05f);
            frt.anchorMax = new Vector2(1, 0.10f);
            frt.offsetMin = Vector2.zero;
            frt.offsetMax = Vector2.zero;

            return ts;
        }

        static void PlaceBtn(Button b, float ymin, float ymax)
        {
            var rt = b.GetComponent<RectTransform>();
            rt.anchorMin = new Vector2(0.20f, ymin);
            rt.anchorMax = new Vector2(0.80f, ymax);
            rt.offsetMin = new Vector2(0, 12); rt.offsetMax = new Vector2(0, -12);
        }

        public void Refresh()
        {
            bool hasSave = GameManager.I != null && GameManager.I.Started;
            _continue.gameObject.SetActive(hasSave);
            if (_continueLabel != null) _continueLabel.text = "Continue";
        }
    }
}
