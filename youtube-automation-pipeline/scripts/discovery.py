"""Content discovery and topic selection."""

import os
import logging
from datetime import datetime

from dotenv import load_dotenv

load_dotenv()
logger = logging.getLogger(__name__)


class TopicDiscovery:
    def __init__(self, config: dict | None = None):
        self.config = config or {}
        discovery_config = self.config.get("discovery", {})
        self.sources = discovery_config.get("sources", ["manual"])
        self.subreddits = discovery_config.get("reddit_subreddits", ["technology"])
        self.max_topics = discovery_config.get("max_topics", 5)

    def discover(self, manual_topic: str | None = None) -> list[dict]:
        """Discover trending topics from configured sources.

        If manual_topic is provided, it takes priority.
        Returns list of topic dicts with title, source, score, etc.
        """
        topics = []

        if manual_topic:
            topics.append({
                "title": manual_topic,
                "source": "manual",
                "score": 100,
                "url": None,
                "discovered_at": datetime.now().isoformat(),
            })
            logger.info("Using manual topic: '%s'", manual_topic)
            return topics

        if "reddit" in self.sources:
            topics.extend(self._discover_reddit())

        if "google_trends" in self.sources:
            topics.extend(self._discover_google_trends())

        topics.sort(key=lambda t: t["score"], reverse=True)
        return topics[: self.max_topics]

    def _discover_reddit(self) -> list[dict]:
        """Fetch trending topics from Reddit."""
        try:
            import praw

            client_id = os.getenv("REDDIT_CLIENT_ID")
            client_secret = os.getenv("REDDIT_CLIENT_SECRET")
            user_agent = os.getenv("REDDIT_USER_AGENT", "youtube-automation/1.0")

            if not client_id or not client_secret:
                logger.warning("Reddit credentials not set, skipping Reddit discovery")
                return []

            reddit = praw.Reddit(
                client_id=client_id,
                client_secret=client_secret,
                user_agent=user_agent,
            )

            topics = []
            for sub_name in self.subreddits:
                subreddit = reddit.subreddit(sub_name)
                for post in subreddit.hot(limit=10):
                    if post.stickied:
                        continue
                    topics.append({
                        "title": post.title,
                        "source": f"reddit/r/{sub_name}",
                        "score": post.score,
                        "url": post.url,
                        "discovered_at": datetime.now().isoformat(),
                    })

            logger.info("Found %d topics from Reddit", len(topics))
            return topics

        except ImportError:
            logger.warning("praw not installed, skipping Reddit discovery")
            return []
        except Exception as e:
            logger.error("Reddit discovery failed: %s", e)
            return []

    def _discover_google_trends(self) -> list[dict]:
        """Fetch trending topics from Google Trends."""
        try:
            from pytrends.request import TrendReq

            niche = self.config.get("channel", {}).get("niche", "technology")
            pytrends = TrendReq()
            trending = pytrends.trending_searches(pn="united_states")

            topics = []
            for i, row in trending.head(self.max_topics).iterrows():
                topic_title = row[0]
                topics.append({
                    "title": topic_title,
                    "source": "google_trends",
                    "score": self.max_topics - i,
                    "url": None,
                    "discovered_at": datetime.now().isoformat(),
                })

            logger.info("Found %d topics from Google Trends", len(topics))
            return topics

        except ImportError:
            logger.warning("pytrends not installed, skipping Google Trends discovery")
            return []
        except Exception as e:
            logger.error("Google Trends discovery failed: %s", e)
            return []

    def select_topic(self, topics: list[dict]) -> dict:
        """Select the best topic from the list (highest score)."""
        if not topics:
            raise ValueError("No topics available to select from")
        selected = topics[0]
        logger.info("Selected topic: '%s' (score: %s)", selected["title"], selected["score"])
        return selected
