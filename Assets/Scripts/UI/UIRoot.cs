using UnityEngine;
using UnityEngine.UI;

namespace CorporateDragon.UI
{
    public class UIRoot : MonoBehaviour
    {
        public Canvas Canvas;
        public TitleScreen Title;
        public HomeScreen Home;
        public EpisodeScreen Episode;
        public AlbumScreen Album;

        public static UIRoot Create()
        {
            var go = new GameObject("UIRoot", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            DontDestroyOnLoad(go);
            var canvas = go.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 100;

            var scaler = go.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 1f;

            var ui = go.AddComponent<UIRoot>();
            ui.Canvas = canvas;
            UIHelpers.EnsureEventSystem();
            ui.BuildScreens();
            return ui;
        }

        void BuildScreens()
        {
            Title = TitleScreen.Build(transform);
            Home = HomeScreen.Build(transform);
            Episode = EpisodeScreen.Build(transform);
            Album = AlbumScreen.Build(transform);

            HideAll();
        }

        public void HideAll()
        {
            Title.gameObject.SetActive(false);
            Home.gameObject.SetActive(false);
            Episode.gameObject.SetActive(false);
            Album.gameObject.SetActive(false);
        }

        public void ShowTitle() { HideAll(); Title.gameObject.SetActive(true); Title.Refresh(); }
        public void ShowHome()  { HideAll(); Home.gameObject.SetActive(true); Home.Refresh(); }
        public void ShowEpisode() { HideAll(); Episode.gameObject.SetActive(true); }
        public void ShowAlbum() { HideAll(); Album.gameObject.SetActive(true); Album.Refresh(); }
    }
}
