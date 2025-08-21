package net.kdt.pojavlaunch.modloaders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class NeoForgeVersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {
    private final LayoutInflater mLayoutInflater;
    private final LinkedHashMap<String, LinkedHashSet<String>> minecraftToLoaderVersionsHashmap;
    private LinkedHashSet<String> generatedHashSet = null;


    public NeoForgeVersionListAdapter(List<String> forgeVersions, LayoutInflater layoutInflater) {
        this.mLayoutInflater = layoutInflater;
        minecraftToLoaderVersionsHashmap = new LinkedHashMap<>();
        JsonArray versionsJsonArray = JsonParser.parseString(forgeVersions.get(0)).getAsJsonObject().getAsJsonArray("versions");

        ArrayList<JsonElement> sortedVersionsList = new ArrayList<>();
        for (JsonElement elem : versionsJsonArray) {
            sortedVersionsList.add(elem);
        }
        Collections.sort(sortedVersionsList, (o1, o2) -> {
            String versionString1 = ((JsonObject) o1).get("requires").getAsJsonArray().get(0).getAsJsonObject().get("equals").getAsString();
            String versionString2 = ((JsonObject) o2).get("requires").getAsJsonArray().get(0).getAsJsonObject().get("equals").getAsString();
            return versionString2.compareTo(versionString1); // Sorts by Minecraft version
        });

        for (JsonElement sortedVersionPick : sortedVersionsList) {
            String loaderVersion = ((JsonObject) sortedVersionPick).get("version").getAsString();
            String minecraftVersion = ((JsonObject) sortedVersionPick).get("requires").getAsJsonArray().get(0).getAsJsonObject().get("equals").getAsString();
            if (minecraftToLoaderVersionsHashmap.containsKey(minecraftVersion)) {
                minecraftToLoaderVersionsHashmap.get(minecraftVersion).add(loaderVersion);
            } else {
                generatedHashSet = new LinkedHashSet<>();
                generatedHashSet.add(loaderVersion);
                minecraftToLoaderVersionsHashmap.put(minecraftVersion, generatedHashSet);
            }
        }
    }
    @Override
    public int getGroupCount() {
        return minecraftToLoaderVersionsHashmap.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return new ArrayList<>(minecraftToLoaderVersionsHashmap.values()).get(i).size();
    }

    @Override
    public Object getGroup(int i) {
        return getGameVersion(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return getForgeVersion(i, i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int i, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);

        ((TextView) convertView).setText(getGameVersion(i));

        return convertView;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);
        ((TextView) convertView).setText(getForgeVersion(i, i1));
        return convertView;
    }

    private String getGameVersion(int i) {
        return minecraftToLoaderVersionsHashmap.keySet().toArray()[i].toString();
    }

    private String getForgeVersion(int i, int i1){
        return new ArrayList<>(minecraftToLoaderVersionsHashmap.values()).get(i).toArray()[i1].toString();
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }
}
