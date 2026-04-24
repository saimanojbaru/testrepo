"""Spotify Mashup Generator — public API surface."""

from .spotify_fetcher import SpotifyFetcher, TrackMetadata
from .audio_downloader import AudioDownloader
from .stem_separator import StemSeparator, Backend
from .audio_manipulator import AudioManipulator
from .audio_mixer import AudioMixer
from .pipeline import MashupPipeline, PipelineConfig

__all__ = [
    "SpotifyFetcher",
    "TrackMetadata",
    "AudioDownloader",
    "StemSeparator",
    "Backend",
    "AudioManipulator",
    "AudioMixer",
    "MashupPipeline",
    "PipelineConfig",
]
