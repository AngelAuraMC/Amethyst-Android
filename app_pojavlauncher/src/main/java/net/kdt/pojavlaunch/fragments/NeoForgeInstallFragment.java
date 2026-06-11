package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeVersionListHandler;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.modloaders.NeoForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.NeoForgeVersionListAdapter;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class NeoForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "NeoForgeInstallFragment";
    public NeoForgeInstallFragment() {
        super(TAG);
    }

    private static final String NEOFORGE_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public int getTitleText() {
        return R.string.neoforge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.neoforge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() {
        return downloadNeoForgeVersions();
    }

    public static List<String> downloadNeoForgeVersions() {
        SAXParser saxParser;
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            saxParser = parserFactory.newSAXParser();
        }catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            // if we cant make a parser we might as well not even try to parse anything
            return null;
        }
        try {
            //of_test();
            return DownloadUtils.downloadStringCached(NEOFORGE_METADATA_URL, "neoforge_versions", input -> {
                try {
                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    return handler.getVersions();
                    // IOException is present here StringReader throws it only if the parser called close()
                    // sooner than needed, which is a parser issue and not an I/O one
                }catch (SAXException | IOException e) {
                    throw new DownloadUtils.ParseException(e);
                }
            });
        }catch (DownloadUtils.ParseException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new NeoForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy listenerProxy) {
        return new NeoForgeDownloadTask(listenerProxy, (String) selectedVersion);
    }

    @Override
    public void onDownloadFinished(Context context, File downloadedFile) {
        Intent modInstallerStartIntent = new Intent(context, JavaGUILauncherActivity.class);
        modInstallerStartIntent
                .putExtra("javaArgs", "-jar "+downloadedFile.getAbsolutePath()+" --install-client")
                .putExtra("openLogOutput", true);
        context.startActivity(modInstallerStartIntent);
    }
}
