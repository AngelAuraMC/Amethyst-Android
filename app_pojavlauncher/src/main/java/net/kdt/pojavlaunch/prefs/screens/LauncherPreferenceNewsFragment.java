package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Xml;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Displays the official Minecraft.net community RSS feed.
 * Headlines and excerpts are attributed to Minecraft.net and article links open on the source site.
 */
public class LauncherPreferenceNewsFragment extends LauncherPreferenceFragment {
    private static final String FEED_URL =
            "https://www.minecraft.net/en-us/feeds/community-content/rss";
    private PreferenceCategory mArticles;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_news);
        mArticles = requirePreference("news_articles", PreferenceCategory.class);
        requirePreference("news_refresh").setOnPreferenceClickListener(preference -> {
            loadFeed();
            return true;
        });
        loadFeed();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mArticles != null && mArticles.getPreferenceCount() == 0) loadFeed();
    }

    private void loadFeed() {
        if (!isAdded() || mArticles == null) return;
        mArticles.removeAll();
        addStatus(R.string.novacraft_news_loading);
        Activity host = getActivity();
        if (host == null) return;

        PojavApplication.sExecutorService.execute(() -> {
            ArrayList<NewsItem> items = null;
            Exception failure = null;
            try {
                items = downloadAndParseFeed();
            } catch (Exception error) {
                failure = error;
            }
            final ArrayList<NewsItem> result = items;
            final Exception error = failure;
            host.runOnUiThread(() -> {
                if (!isAdded() || mArticles == null) return;
                mArticles.removeAll();
                if (error != null) {
                    addStatus(R.string.novacraft_news_error);
                    return;
                }
                if (result == null || result.isEmpty()) {
                    addStatus(R.string.novacraft_news_empty);
                    return;
                }
                for (NewsItem item : result) addNewsItem(item);
            });
        });
    }

    private ArrayList<NewsItem> downloadAndParseFeed() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "NovaCraft Android Launcher");
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
        try {
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) throw new java.io.IOException("HTTP " + response);
            try (InputStream input = connection.getInputStream()) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
                parser.setInput(input, StandardCharsets.UTF_8.name());
                ArrayList<NewsItem> entries = new ArrayList<>();
                NewsItem current = null;
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("item".equalsIgnoreCase(tag)) {
                            current = new NewsItem();
                        } else if (current != null) {
                            if ("title".equalsIgnoreCase(tag)) current.title = parser.nextText();
                            else if ("link".equalsIgnoreCase(tag)) current.link = parser.nextText();
                            else if ("description".equalsIgnoreCase(tag)) current.description = parser.nextText();
                            else if ("pubDate".equalsIgnoreCase(tag)) current.date = parser.nextText();
                        }
                    } else if (event == XmlPullParser.END_TAG
                            && "item".equalsIgnoreCase(parser.getName()) && current != null) {
                        if (!TextUtils.isEmpty(current.title) && isOfficialArticleLink(current.link)) {
                            entries.add(current);
                        }
                        current = null;
                        if (entries.size() >= 60) break;
                    }
                    event = parser.next();
                }
                return entries;
            }
        } finally {
            connection.disconnect();
        }
    }

    private boolean isOfficialArticleLink(String value) {
        if (TextUtils.isEmpty(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ("www.minecraft.net".equalsIgnoreCase(host)
                    || "minecraft.net".equalsIgnoreCase(host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void addNewsItem(NewsItem item) {
        Preference preference = new Preference(requireContext());
        preference.setTitle(Html.fromHtml(item.title).toString().trim());
        String summary = "";
        if (!TextUtils.isEmpty(item.date)) summary = item.date.trim();
        if (!TextUtils.isEmpty(item.description)) {
            String excerpt = Html.fromHtml(item.description).toString().replaceAll("\\s+", " ").trim();
            if (excerpt.length() > 180) excerpt = excerpt.substring(0, 177) + "…";
            if (!excerpt.isEmpty()) summary = summary.isEmpty() ? excerpt : summary + "\\n" + excerpt;
        }
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(clicked -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.link)));
            } catch (Exception error) {
                Toast.makeText(requireContext(), R.string.novacraft_news_open_error,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        mArticles.addPreference(preference);
    }

    private void addStatus(int textId) {
        Preference status = new Preference(requireContext());
        status.setTitle(textId);
        status.setSelectable(false);
        status.setPersistent(false);
        mArticles.addPreference(status);
    }

    private static class NewsItem {
        String title;
        String link;
        String description;
        String date;
    }
}
