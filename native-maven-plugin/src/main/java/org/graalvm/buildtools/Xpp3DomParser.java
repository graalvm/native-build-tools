package org.graalvm.buildtools;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.LinkedList;

public class Xpp3DomParser {

    public static Xpp3Dom getTagByName(Xpp3Dom root, String name) {
        if (root.getName().equalsIgnoreCase(name)){
            return root;
        }

        Xpp3Dom[] children = root.getChildren();
        for (Xpp3Dom child : children) {
            Xpp3Dom retVal = getTagByName(child, name);
            if (retVal != null) {
                return retVal;
            }
        }

        return null;
    }

    public static LinkedList<Xpp3Dom> getAllTagsByName(Xpp3Dom root, String name) {
        LinkedList<Xpp3Dom> listOfTags = new LinkedList<>();
        if (root.getName().equalsIgnoreCase(name)){
            listOfTags.add(root);
        }

        Xpp3Dom[] children = root.getChildren();
        for (Xpp3Dom child : children) {
            Xpp3Dom retVal = getTagByName(child, name);
            if (retVal != null) {
                listOfTags.add(retVal);
            }
        }

        return listOfTags;
    }

}
